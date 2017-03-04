
#define LOG_TAG "VideoShareSlave"
#define BUFFER_SIZE 4096

//for use the GL & EGL extension
#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#define UpAlign4(n) (((n) + 3) & ~3)
#define ALIGN(x,y) ((x + y - 1) & ~(y - 1))

#include <sys/resource.h>
#include <binder/IPCThreadState.h>
#include <utils/Log.h>
#include <cutils/atomic.h>
#include <time.h>
#include <utils/Trace.h>
#include <cutils/properties.h>

#include "VideoShareSlave.h"
#include <setjmp.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferMapper.h>
#include <media/stagefright/foundation/ADebug.h> 


extern "C" {
	#include "jpeglib.h" 
	#include "jerror.h"
}

#define rgbtoy(b, g, r, y) \
    y=(unsigned char)(((int)(30*r) + (int)(59*g) + (int)(11*b))/100)

#define rgbtoyuv(b, g, r, y, u, v) \
    rgbtoy(b, g, r, y); \
    u=(unsigned char)(((int)(-17*r) - (int)(33*g) + (int)(50*b)+12800)/100); \
    v=(unsigned char)(((int)(50*r) - (int)(42*g) - (int)(8*b)+12800)/100)


namespace android{
	
static		int			mServerSocketFd;
static		int 		mClientSocketFd;
static		int 		mServerAddrLen;
static		int 		mClientAddrLen;
static		struct sockaddr_in 			mServerAddress;
static		struct sockaddr_in 			mClientAddress;

static		Mutex						mPlayerLock;
static		Condition					mPlayerCondition;

static	int 						mCurrentIndex;
static	int 						mCurrentSize;
static	bool						mJpegBufInUse;
static	int							mCurrentWidth;
static	int							mCurrentHeight;
static	int							mSplitPlay;


	
//----------------------------------------------------------------------------------------------------
//socket read
int socket_read(int fd,unsigned char *buffer,int length) 
{ 
	//ALOGE("vaylb-->socket_read, total size = %d",length);
	int i = length;
    int ret = 0;
    while(i > 0 && (ret = read(fd,buffer + (length - i),i)) > 0)
    {
           i -= ret;
    }
    return (i == 0)?length:0;
}

//----------------------------------------------------------------------------------------------------
//JPEG deCompress
static void init_source (j_decompress_ptr cinfo) {}
static boolean fill_input_buffer (j_decompress_ptr cinfo)
{
    ERREXIT(cinfo, JERR_INPUT_EMPTY);
	return TRUE;
}
static void skip_input_data (j_decompress_ptr cinfo, long num_bytes)
{
    struct jpeg_source_mgr* src = (struct jpeg_source_mgr*) cinfo->src;

    if (num_bytes > 0) {
        src->next_input_byte += (size_t) num_bytes;
        src->bytes_in_buffer -= (size_t) num_bytes;
    }
}
static void term_source (j_decompress_ptr cinfo) {}
static void jpeg_mem_src (j_decompress_ptr cinfo, void* buffer, long nbytes)
{
    struct jpeg_source_mgr* src;

    if (cinfo->src == NULL) {   /* first time for this JPEG object? */
        cinfo->src = (struct jpeg_source_mgr *)
            (*cinfo->mem->alloc_small) ((j_common_ptr) cinfo, JPOOL_PERMANENT,
            sizeof(struct jpeg_source_mgr));
    }

    src = (struct jpeg_source_mgr*) cinfo->src;
    src->init_source = init_source;
    src->fill_input_buffer = fill_input_buffer;
    src->skip_input_data = skip_input_data;
    src->resync_to_restart = jpeg_resync_to_restart; /* use default method */
    src->term_source = term_source;
    src->bytes_in_buffer = nbytes;
    src->next_input_byte = (JOCTET*)buffer;
}


//----------------------------------------------------------------------------------------------------
//refer to http://kerlubasola.iteye.com/blog/1579736
static int rgb888_to_rgb565(const void * psrc, int w, int h, void * pdst)    
{    
    int srclinesize = UpAlign4(w * 3);    
    int dstlinesize = UpAlign4(w * 2);    
        
    const unsigned char * psrcline;    
    const unsigned char * psrcdot;    
    unsigned char  * pdstline;    
    unsigned short * pdstdot;    
        
    int i,j;    
        
    if (!psrc || !pdst || w <= 0 || h <= 0) {    
        ALOGE("rgb888_to_rgb565 : parameter error");    
        return -1;    
    }    
    
    psrcline = (const unsigned char *)psrc;    
    pdstline = (unsigned char *)pdst;    
    for (i=0; i<h; i++) {    
        psrcdot = psrcline;    
        pdstdot = (unsigned short *)pdstline;    
        for (j=0; j<w; j++) {    
            //888 r|g|b -> 565 b|g|r    
            *pdstdot =  (((psrcdot[0] >> 3) & 0x1F) << 0)//r    
                        |(((psrcdot[1] >> 2) & 0x3F) << 5)//g    
                        |(((psrcdot[2] >> 3) & 0x1F) << 11);//b    
            psrcdot += 3;    
            pdstdot++;    
        }    
        psrcline += srclinesize;    
        pdstline += dstlinesize;    
    }    
    
    return 0;    
} 

static void RGBtoYUV420PSameSize (
	const unsigned char * rgb,
    unsigned char * yuv,
    unsigned rgbIncrement,
    bool flip,
    int srcFrameWidth, int srcFrameHeight)
{
    unsigned int planeSize;
    unsigned int halfWidth;

    unsigned char * yplane;
    unsigned char * uplane;
    unsigned char * vplane;
    const unsigned char * rgbIndex;

    int x, y;
    unsigned char * yline;
    unsigned char * uline;
    unsigned char * vline;

    planeSize = srcFrameWidth * srcFrameHeight;
    halfWidth = srcFrameWidth >> 1;

    // get pointers to the data
    yplane = yuv;
    uplane = yuv + planeSize;
    vplane = yuv + planeSize + (planeSize >> 2);
    rgbIndex = rgb;

    for (y = 0; y < srcFrameHeight; y++)
    {
        yline = yplane + (y * srcFrameWidth);
        uline = uplane + ((y >> 1) * halfWidth);
        vline = vplane + ((y >> 1) * halfWidth);

        if (flip)
            rgbIndex = rgb + (srcFrameWidth*(srcFrameHeight-1-y)*rgbIncrement);

        for (x = 0; x < (int) srcFrameWidth; x+=2)
        {
            rgbtoyuv(rgbIndex[0], rgbIndex[1], rgbIndex[2], *yline, *uline, *vline);
            rgbIndex += rgbIncrement;
            yline++;
            rgbtoyuv(rgbIndex[0], rgbIndex[1], rgbIndex[2], *yline, *uline, *vline);
            rgbIndex += rgbIncrement;
            yline++;
            uline++;
            vline++;
        }
    }
}


//----------------------------------------------------------------------------------------------------
static void checkGlError(const char* op) {
	for (GLint error = glGetError(); error; error = glGetError()) {
		ALOGE("vaylb-->after %s() glError (0x%x)\n", op, error);
	}
}

//----------------------------------------------------------------------------------------------------

long get_time_stamp(){ //ms
	struct timeval tv;	  
	gettimeofday(&tv,NULL);    
	return (tv.tv_sec * 1000 + tv.tv_usec / 1000);
}
//----------------------------------------------------------------------------------------------------


VideoShareSlave::VideoShareSlave(/*sp<BufferQueue> bufferQueue*/):
	mImageBuf(NULL),
	mVideoDecodecor(new VideoDecodecor(this)),
	mVideoPlayer(new VideoPlayer(this)),
	mReceiveBuffer(new DataBuffer(1024*512))
{
	/*
	for(int i = 0; i < RECV_BUF_COUNT; i++){
		mRecvBuf[i] = (unsigned char*)malloc(RECV_BUF_SIZE);
		mRecvBufState[i] = can_write;
	}
	*/
	mJpegBufInUse = false;
	mJpegBuf = (unsigned char*)malloc(1920*1080*2);
	mSplitPlay = false;
	ALOGE("vaylb_test-->Slave VideoShare construct.");
}

VideoShareSlave::~VideoShareSlave()
{
	ALOGE("vaylb_test-->Slave VideoShare destruct.");
	/*
	for(int i = 0; i < RECV_BUF_COUNT; i++){
		free(mRecvBuf[i]);
		mRecvBuf[i] = NULL;
	}
	*/
	if(mVideoDecodecor != NULL) mVideoDecodecor->~VideoDecodecor();
	if(mVideoPlayer != NULL) mVideoPlayer->~VideoPlayer();
	if(mImageBuf != NULL) {
		free(mImageBuf);
		mImageBuf = NULL;
	}
	if(mReceiveBuffer != NULL){
		delete mReceiveBuffer;
	}
	if(mJpegBuf!= NULL) {
		free(mJpegBuf);
		mJpegBuf = NULL;
	}
}

status_t VideoShareSlave::setUpReceiver(String16 ip){
	ALOGE("vaylb-->Slave VideoShare setUpReceiver");
	mHostIp = ip;
	int res;

	mServerSocketFd = socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(mServerSocketFd == -1){
		ALOGE("vaylb-->create socket error:%d",errno); //errno13 :    Permission denied
		return -1;
	}
	ALOGE("vaylb-->create socket fd = %d",mServerSocketFd);
	mServerAddress.sin_family = AF_INET;
	mServerAddress.sin_addr.s_addr = inet_addr((const char*)(mHostIp.string()));
	ALOGE("vaylb-->VideoShareSlave server ip = %s",(const char*)(mHostIp.string()));
	mServerAddress.sin_port = htons(40004);
	mServerAddrLen = sizeof(mServerAddress);
	res = connect(mServerSocketFd,(struct sockaddr*)&mServerAddress, mServerAddrLen);
	if(res == -1){
		ALOGE("vaylb-->connect to slave fd = %d, error:%d",mServerSocketFd,errno);
		return -1;
	}

	return NO_ERROR;
}

bool VideoShareSlave::setVideoSurface(sp < Surface > & surface){
	mSurface = surface;
	if(android::Surface::isValid(mSurface)) {
		ALOGE("vaylb-->VideoShare surface is valid ");  
		start_threads();
		return true;
	}
	else return false;
}

void VideoShareSlave::setSplitPlay(int split){
	ALOGE("vaylb-->VideoShareSlave setSplitPlay to %d",split);
	mSplitPlay = split;
}

void VideoShareSlave::start_threads(){
	if(mVideoDecodecor != NULL) mVideoDecodecor->threadLoop_run();
	if(mVideoPlayer != NULL) mVideoPlayer->threadLoop_run();
}

void VideoShareSlave::stop_threads(){
	if(mVideoDecodecor != NULL) mVideoDecodecor->threadLoop_exit();
	if(mVideoPlayer != NULL) mVideoPlayer->threadLoop_exit();
	mVideoDecodecor->signalVideoPlayer();
}

//-----------------------------------------------------------------------------
//                        				VideoDecodecor                     
//-----------------------------------------------------------------------------
VideoShareSlave::VideoDecodecor::VideoDecodecor(VideoShareSlave * videoshareslave)
	:Thread(false /*canCallJava*/),
	mVideoShareSlave(videoshareslave)
{
	ALOGE("vaylb-->VideoDecodecor construct.");
}

VideoShareSlave::VideoDecodecor::~VideoDecodecor()
{
	ALOGE("vaylb-->VideoDecodecor destruct.");
	mVideoShareSlave.clear();
	// mVideoShareSlave = NULL;
}

bool VideoShareSlave::VideoDecodecor::threadLoop()
{
	ALOGE("vaylb-->VideoDecodecor  threadLoop");
	if(mServerSocketFd <= 0){
		ALOGE("vaylb-->VideoDecodecor threadloop , socket error, mServerSocketFd:%d",mServerSocketFd);
		return false;
	}


	int recId = -1;
	int count = 0;
	while (!exitPending())
    {
		int receive = 0;
		read(mServerSocketFd,(void*)&receive,sizeof(receive));
		long time1 = get_time_stamp();
		int size = ntohl(receive)-sizeof(receive);
		if(size <= 0) continue;
		unsigned char* image = (unsigned char*)malloc(size);

		if(socket_read(mServerSocketFd,image,size)){
			int datasize = size + sizeof(size);
			while(!exitPending() && mVideoShareSlave->mReceiveBuffer->getWriteSpace() < datasize){
				signalVideoPlayer();
				sleep(10000000); //10ms
			}
			mVideoShareSlave->mReceiveBuffer->Write((char*)&size,sizeof(size));
			mVideoShareSlave->mReceiveBuffer->Write((char*)image,size);
			count++;
			signalVideoPlayer();
		}
		
		free(image);
		image = NULL;
    }
	signalVideoPlayer();
	close(mServerSocketFd);
	close(mClientSocketFd);
	ALOGE("vaylb-->VideoDecodecor::threadLoop end.");
    return false;
}

void VideoShareSlave::VideoDecodecor::sleep(long sleepNs){
	const struct timespec req = {0, sleepNs};
	nanosleep(&req, NULL);
}

void VideoShareSlave::VideoDecodecor::signalVideoPlayer(){
	Mutex::Autolock _l(mPlayerLock);
	mPlayerCondition.signal();
}

void VideoShareSlave::VideoDecodecor::threadLoop_run(){
	run("VideoDecodecor", PRIORITY_URGENT_DISPLAY);
}

void VideoShareSlave::VideoDecodecor::threadLoop_exit(){
	close(mClientSocketFd);
	close(mServerSocketFd);
	this->requestExit();
	//this->requestExitAndWait();
}


//-----------------------------------------------------------------------------
//                        				VideoPlayer                    
//-----------------------------------------------------------------------------
VideoShareSlave::VideoPlayer::VideoPlayer(VideoShareSlave * videoshareslave)
	:Thread(false /*canCallJava*/),
	mVideoShareSlave(videoshareslave),
	mPlayBuf(NULL)
{
	ALOGE("vaylb-->VideoPlayer construct.");
}

VideoShareSlave::VideoPlayer::~VideoPlayer()
{
	ALOGE("vaylb-->VideoPlayer destruct.");
	mVideoShareSlave.clear();
	mVideoShareSlave = NULL;
	if(mPlayBuf) free(mPlayBuf);
	mPlayBuf = NULL;
}

bool VideoShareSlave::VideoPlayer::threadLoop()
{
	ALOGE("vaylb-->VideoPlayer  threadLoop");
	
	while (!exitPending())
    {
    	Mutex::Autolock _l(mPlayerLock);
		mPlayerCondition.wait(mPlayerLock);

		int datasize = 0;
		while(mVideoShareSlave->mReceiveBuffer->getReadSpace() > sizeof(datasize)){
			mVideoShareSlave->mReceiveBuffer->Read((char*)&datasize,sizeof(datasize));
			if(datasize>0){
				while(mVideoShareSlave->mReceiveBuffer->getReadSpace() < datasize){
					ALOGE("vaylb-->VideoPlayer waiting for DataBuffer can read %d byte",datasize);
					sleep(1000000); //1ms
				}
				mVideoShareSlave->mReceiveBuffer->Read((char*)mVideoShareSlave->mJpegBuf,datasize);
				mCurrentWidth = mCurrentHeight = 0;
				deCompressFromJPEG(mVideoShareSlave->mJpegBuf,datasize,&(mVideoShareSlave->mImageBuf),&mCurrentWidth,&mCurrentHeight);
				videoPlayBack(mVideoShareSlave->mImageBuf,mCurrentWidth,mCurrentHeight);
			}else{
				ALOGE("get frame size error");
			}
		}
    }
	ALOGE("vaylb_test-->VideoPlayer threadLoop end.");
    return false;
}

void VideoShareSlave::VideoPlayer::deCompressFromJPEG(
		unsigned char* src_buffer,unsigned long src_size,unsigned char** dest_buffer,int* dest_width,int * dest_height){
	//ALOGE("vaylb-->start decompress");
	struct jpeg_decompress_struct cinfo;
    struct jpeg_error_mgr jerr;
	int					res;
	unsigned long 		bmp_size;
	unsigned int 		i;
 
    cinfo.err = jpeg_std_error(&jerr);
	
    jpeg_create_decompress(&cinfo);
 
    jpeg_mem_src(&cinfo, (void*)src_buffer,src_size);
	
  	res = jpeg_read_header(&cinfo, TRUE);
	if(res < 0){
		ALOGE("vaylb-->File does not seem to be a normal JPEG");
		return;
	}
        
    cinfo.out_color_space = JCS_YCbCr;
    cinfo.raw_data_out = TRUE;
    cinfo.do_fancy_upsampling = FALSE;
 
    jpeg_start_decompress(&cinfo);
 
 
    bmp_size = cinfo.output_width*cinfo.output_height*1.5;
	if(*dest_buffer == NULL) *dest_buffer = (unsigned char *)malloc(bmp_size);
 
    JSAMPARRAY dst_buf[3];
    JSAMPROW   dst_row1[cinfo.output_height];
    JSAMPROW   dst_row2[cinfo.output_height>>1];
    JSAMPROW   dst_row3[cinfo.output_height>>1];
     
    dst_buf[0] = dst_row1;
 
    for(i = 0; i < cinfo.output_height;i++)
    {
        dst_buf[0][i] = *dest_buffer + i * cinfo.output_width;
    }
 
    unsigned char * p_next_comp;

	dst_buf[2] = dst_row3;
    p_next_comp = *dest_buffer + cinfo.output_width * cinfo.output_height;
      
    for(i = 0; i < cinfo.output_height>>1;i++)
    {
        dst_buf[2][i] = p_next_comp + i * (cinfo.output_width>>1);
    }
 
	dst_buf[1] = dst_row2;
	p_next_comp = p_next_comp + cinfo.output_width * (cinfo.output_height>>2);
 
    for(i = 0; i < cinfo.output_height>>1;i++)
    {
        dst_buf[1][i] = p_next_comp + i * (cinfo.output_width>>1);
    }

 	int	y_read_line = 16;
    while (cinfo.output_scanline < cinfo.output_height)
    {
        jpeg_read_raw_data(&cinfo, dst_buf, y_read_line);
 
        dst_buf[0] += y_read_line;
        dst_buf[1] += (y_read_line>>1);
        dst_buf[2] += (y_read_line>>1);
    }
	
 	*dest_width = cinfo.output_width;
	*dest_height = cinfo.output_height;
    jpeg_finish_decompress(&cinfo);    
    jpeg_destroy_decompress(&cinfo);
	//ALOGE("vaylb-->decompress success width = %d, height = %d",*dest_width,*dest_height);
}


void VideoShareSlave::VideoPlayer::videoPlayBack(unsigned char * image_buf,int width,int height){
	//long time1 = get_time_stamp();
	/*
	unsigned char * image_yuv = (unsigned char*)malloc(width*height*1.5);
	RGBtoYUV420PSameSize(image_buf,image_yuv,3,false,width,height);
	mJpegBufInUse = false;
	long time2 = get_time_stamp();
	frameRender(image_yuv,mVideoShareSlave->mSurface,width,height);
	free(image_yuv);
	ALOGE("vaylb-->playback new frame time use: format %ld, display %ld",time2-time1,get_time_stamp()-time2);
	*/
	if(mPlayBuf == NULL) mPlayBuf = (unsigned char*)malloc(width*height*1.5);
	
	//YUV420Rotate90(mPlayBuf,image_buf,width,height,90);
	if(mSplitPlay){
		memset(mPlayBuf,0,width*height);
		memset(mPlayBuf+width*height,128,width*height/2);
		clip_resize(mPlayBuf,image_buf,width,height);
		mJpegBufInUse = false;
		frameRender(mPlayBuf,mVideoShareSlave->mSurface,height,width,false);
	}else{
		YUV420Rotate90(mPlayBuf,image_buf,width,height,90);
		mJpegBufInUse = false;
		frameRender(mPlayBuf,mVideoShareSlave->mSurface,height,width,true);
	}
}

void VideoShareSlave::VideoPlayer::clip_resize(unsigned char * dst,unsigned char * src,int width,int height){
	int clip_width = width>>1, clip_height = height;
	unsigned long clipsize = clip_width*clip_height*1.5;
	unsigned char* clipregion = (unsigned char*)malloc(clipsize);
	//Y planer
	for(int x = 0; x < clip_height; x++){ 
		memcpy(clipregion+x*clip_width,src+x*width+clip_width,clip_width);
	}
	//U V (two) planer (YV12) to UV (one) planer (NV12)
	int srcsize_Y = width*height,srcsize_U_V = srcsize_Y>>2,clipsize_Y =  clip_width*clip_height;
	int src_U_V_width = width>>1,clip_UV_width = clip_width>>1, clip_UV_height = clip_height>>1;
	int i = clipsize_Y-1;
	for(int x = 0; x < clip_UV_height; x++){
		for(int y = 0; y < clip_UV_width; y++){
			clipregion[i++] = src[srcsize_Y+x*src_U_V_width+clip_UV_width+y];
			clipregion[i++] = src[srcsize_Y+srcsize_U_V+x*src_U_V_width+clip_UV_width+y];
		}
	}
	
	int resize_w = height,resize_h = height*height*2/width;
	int resize_U_V_w = resize_w>>1,resize_U_V_h = resize_h>>1;
	long resize_size = resize_w*resize_h*1.5;
	long resize_size_Y = resize_w*resize_h,resize_size_U_V = resize_size_Y>>2;
	unsigned char* resize_addr = (unsigned char*)malloc(resize_size);
	if(mVideoRescale == NULL) mVideoRescale = new nv12rescale();
	mVideoRescale->ImageResize(clipregion,resize_addr,clip_width,clip_height,resize_w,resize_h);

	int shift_top = (width-resize_h)/2;
	if(shift_top%2) shift_top-=1;
	int shift_top_size_Y = shift_top*resize_w,shift_top_size_U_V = shift_top_size_Y>>2;
	
	memcpy(dst+shift_top_size_Y,resize_addr,resize_size_Y-20*resize_w);
	//memcpy(dst,resize_addr,resize_size_Y);

	unsigned char* resize_U = (unsigned char*)malloc(resize_size_U_V);
	unsigned char* resize_V = (unsigned char*)malloc(resize_size_U_V);
	i = 0;
	for(int x = 0; x < resize_size_U_V*2; x+=2){
		resize_U[i] = resize_addr[resize_size_Y+x];
		resize_V[i] = resize_addr[resize_size_Y+x+1];
		i++;
	}

	memcpy(dst+srcsize_Y+shift_top_size_U_V,resize_V,resize_size_U_V-5*resize_w);
	memcpy(dst+srcsize_Y+srcsize_U_V+shift_top_size_U_V,resize_U,resize_size_U_V-5*resize_w);
	
	//memcpy(dst+srcsize_Y,resize_V,resize_size_U_V);
	//memcpy(dst+srcsize_Y+srcsize_U_V,resize_U,resize_size_U_V);

	/*
	i = resize_size_Y-1;
	for(int m = 0; m < resize_U_V_h; m++){
		for(int n = 0; n < resize_U_V_w; n++){
			dst[srcsize_Y+shift_top_size_U_V+m*resize_U_V_w+n] = resize_addr[i];
			i++;
			dst[srcsize_Y+srcsize_U_V+shift_top_size_U_V+m*resize_U_V_w+n] = resize_addr[i];
			i++;
		}
	}
	*/
	
	/*
	i = resize_size_Y+resize_size_U_V-1;
	for(int m = 0; m < resize_U_V_h; m++){
		for(int n = 0; n < resize_U_V_w; n++){
			dst[srcsize_Y+srcsize_U_V+shift_top_size_U_V+m*resize_U_V_w+n] = resize_addr[i];
			//dst[srcsize_Y+shift_top_size_U_V+m*resize_U_V_w+n] = resize_addr[i];
			i++;
		}
	}
	*/
		
	free(clipregion);
	free(resize_addr);

	free(resize_U);
	free(resize_V);
	clipregion = NULL;
	resize_addr = NULL;
	resize_U = NULL;
	resize_V = NULL;
}

void VideoShareSlave::VideoPlayer::YUV420Rotate90(unsigned char *dst,unsigned char * src, int imageWidth, int imageHeight,int rotation){
	int i = 0;
    for(int x = 0;x < imageWidth;x++)
    {
        for(int y = imageHeight-1;y >= 0;y--)                               
        {
            dst[i] = src[y*imageWidth+x];
            i++;
        }
    }
    // Rotate the U and V color components 
	int Y_size =  imageWidth*imageHeight;
	int UV_width = imageWidth>>1, UV_height = imageHeight>>1;
	
	i = Y_size-1;
	for(int x = 0;x < UV_width;x++)
    {
        for(int y = UV_height-1;y >= 0;y--)                               
        {
            dst[i] = src[Y_size+y*UV_width+x];
            i++;
        }
    }

	int UV_size = UV_width*UV_height;

	i = Y_size+UV_size-1;
	for(int x = 0;x < UV_width;x++)
    {
        for(int y = UV_height-1;y >= 0;y--)                               
        {
            dst[i] = src[Y_size+UV_size+y*UV_width+x];
            i++;
        }
    }
	
	/*
    i = imageWidth*imageHeight*3/2-1;
    for(int x = imageWidth-1;x > 0;x=x-2)
    {
        for(int y = 0;y < imageHeight/2;y++)                                
        {
            //dst[i] = src[(imageWidth*imageHeight)+(y*imageWidth)+x];
            dst[i] = src[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
            i--;
            //dst[i] = src[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
            dst[i] = src[(imageWidth*imageHeight)+(y*imageWidth)+x];
            i--;
        }
    }
    */
}


void VideoShareSlave::VideoPlayer::frameRender(const void * data,const sp < ANativeWindow > & nativeWindow,int width,int height, bool flag){
	sp<ANativeWindow> mNativeWindow = nativeWindow;  
    int err;  
    int mCropWidth = width;  
    int mCropHeight = height;  
      
    int halFormat = HAL_PIXEL_FORMAT_YV12;
    int bufWidth = (mCropWidth + 1) & ~1;
    int bufHeight = (mCropHeight + 1) & ~1;  
      
    CHECK_EQ(0,  
            native_window_set_usage(  
            mNativeWindow.get(),  
            GRALLOC_USAGE_SW_READ_NEVER | GRALLOC_USAGE_SW_WRITE_OFTEN  
            | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP));  
  
    CHECK_EQ(0,  
            native_window_set_scaling_mode(  
            mNativeWindow.get(),  
            NATIVE_WINDOW_SCALING_MODE_SCALE_CROP));  
  
    CHECK_EQ(0, native_window_set_buffers_geometry(  
                mNativeWindow.get(),  
                bufWidth,  
                bufHeight,  
                halFormat));  
      
      
    ANativeWindowBuffer *buf;
    if ((err = native_window_dequeue_buffer_and_wait(mNativeWindow.get(),&buf)) != 0) {  
        ALOGW("vaylb-->slave ::dequeueBuffer returned error %d", err);  
        return;  
    }  
  
    GraphicBufferMapper &mapper = GraphicBufferMapper::get();  
  
    Rect bounds(mCropWidth, mCropHeight);  
  
    void *dst;  
    CHECK_EQ(0, mapper.lock(buf->handle, GRALLOC_USAGE_SW_WRITE_OFTEN, bounds, &dst));
     
    size_t dst_y_size = buf->stride * buf->height;  
    size_t dst_c_stride = ALIGN((buf->stride>>1), 16);
    size_t dst_c_size = dst_c_stride * (buf->height>>1);

	//ALOGE("vaylb-->frame rander width = %d, height = %d, buf->stride = %d, buf->height = %d",bufWidth,bufHeight,buf->stride,buf->height);
          
    memcpy(dst, data, dst_y_size + dst_c_size*2); 
	if(flag){
		for(int i = 0; i < bufHeight; i++){
			memset(dst+i*bufWidth, 0, 10);
			memset(dst+i*bufWidth+bufWidth-2, 0, 2);
		}
		for(int i = 0; i < bufHeight>>1; i++){
			memset(dst+dst_y_size+i*(bufWidth>>1), 128, 10);
			memset(dst+dst_y_size+i*(bufWidth>>1)+(bufWidth>>1)-2, 128, 2);
			memset(dst+dst_y_size+dst_c_size+i*(bufWidth>>1), 128, 10);
			memset(dst+dst_y_size+dst_c_size+i*(bufWidth>>1)+(bufWidth>>1)-2, 128, 2);
		}
	}
  
    CHECK_EQ(0, mapper.unlock(buf->handle));  
  
    if ((err = mNativeWindow->queueBuffer(mNativeWindow.get(), buf,-1)) != 0) {  
        ALOGW("vaylb-->slave::queueBuffer returned error %d", err);  
    }  
    buf = NULL;
}

void VideoShareSlave::VideoPlayer::threadLoop_run(){
	run("VideoPlayer", PRIORITY_URGENT_DISPLAY);
}

void VideoShareSlave::VideoPlayer::threadLoop_exit(){
	this->requestExit();
	//this->requestExitAndWait();
}
void VideoShareSlave::VideoPlayer::sleep(long sleepNs){
	const struct timespec req = {0, sleepNs};
	nanosleep(&req, NULL);
}

}; // namespace android
