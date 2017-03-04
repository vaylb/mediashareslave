

#ifndef ANDROID_VIDEO_SHARE_SLAVE_H
#define ANDROID_VIDEO_SHARE_SLAVE_H

#include <utils/threads.h>
#include <utils/Debug.h>
#include <utils/Thread.h>
#include <pthread.h>

//vaylb for socket
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <gui/Surface.h>
#include <nv12-rescale/nv12rescale.h>
#include "DataBuffer.h"

#define RECV_BUF_COUNT 4
#define	RECV_BUF_SIZE 524288

namespace android {

class VideoShareSlave : public virtual RefBase
{
public:

            	 VideoShareSlave();

protected:
          virtual ~VideoShareSlave();
public:
				status_t 	setUpReceiver(String16 ip);
				bool		setVideoSurface(sp<Surface>& surface);
				void		sleep(long time);
				void		setSplitPlay(int split);
				void 		start_threads();
				void 		stop_threads();

	enum BufState{
		can_read = 0,
		can_write
	};
	
	int 						mIndex;
	String16					mHostIp;
	sp<Surface>					mSurface;
	//unsigned char*				mRecvBuf[RECV_BUF_COUNT];	
	//BufState					mRecvBufState[RECV_BUF_COUNT];
	
	unsigned char*				mImageBuf;
	unsigned char*				mJpegBuf;
	DataBuffer *				mReceiveBuffer;



	class VideoDecodecor: public Thread {

	public:
								VideoDecodecor(VideoShareSlave* videoshareslave);
    	virtual 				~VideoDecodecor();

    	virtual     bool        threadLoop();
		

					void        threadLoop_exit();
					void        threadLoop_run();
					void		signalVideoPlayer();
					void		sleep(long time);
//					int			getEmptyBufferId();

	private:
		sp<VideoShareSlave>		mVideoShareSlave;
	};  // class VideoCompressor

	class VideoPlayer : public Thread {

	public:
			VideoPlayer(VideoShareSlave* host);
    		virtual ~VideoPlayer();
		    virtual     bool        threadLoop();
			void		deCompressFromJPEG(unsigned char * src_buffer,unsigned long src_size,unsigned char** outbuf,int *width,int *height);
			void		clip_resize(unsigned char* dst, unsigned char* src, int width, int height);
			void		YUV420Rotate90(unsigned char * dest,unsigned char * src, int width, int height, int rotation);
			void		videoPlayBack(unsigned char * image_buf,int width,int height);
			void		frameRender(const void *data, const sp<ANativeWindow> &nativeWindow,int width,int height, bool flag);
			void        threadLoop_exit();
			void        threadLoop_run();
			void		sleep(long time);

	private:
		sp<VideoShareSlave>		mVideoShareSlave;
		unsigned char*			mPlayBuf;
		sp<nv12rescale>			mVideoRescale;
	};  // class HostPlayThread

	private:
		sp<VideoDecodecor>		mVideoDecodecor;
		sp<VideoPlayer>			mVideoPlayer;
};



}; // namespace android

#endif // ANDROID_VIDEO_SHARE_SLAVE_H
