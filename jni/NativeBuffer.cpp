#define LOG_TAG "NativeBuffer"
#define DGB 1
#include"NativeBuffer.h"
#include "log.h"

NativeBuffer::NativeBuffer(char *buf,size_t size){
	ALOGD("pzhao-->create NativeBuffer");
	buffer=buf;
	bufsize=size;
	write_ptr=0;
	read_ptr=0;
}
NativeBuffer::~NativeBuffer(){
	// if(buffer!=NULL)
	// 	free(buffer);
	ALOGD("pzhao-->delete NativeBuffer");
}
size_t NativeBuffer::getReadSpace(){
	return write_ptr-read_ptr;
}

size_t NativeBuffer::getWriteSpace(){
	return bufsize-(write_ptr-read_ptr);
}

void NativeBuffer::setWritePos(size_t pos){
	write_ptr=pos;
}

size_t NativeBuffer::Read( char *dest, size_t cnt){
	//ALOGD("pzhao->readpos%d,writepos%d",read_ptr,write_ptr);
	size_t curptr=read_ptr%bufsize;
	if(bufsize-curptr>=cnt){
		memcpy(dest,buffer+curptr,cnt);
		read_ptr+=cnt;
		return cnt;
	}else{
		size_t n1=bufsize-curptr;
		memcpy(dest,buffer+curptr,n1);
		size_t n2=cnt-n1;
		memcpy(dest+n1,buffer,n2);
		read_ptr+=cnt;
		return cnt;
	}
}

int NativeBuffer::ReadAhead(int cnt){
	int readahead = 0;
	if(cnt >= 0){ //read ahead
		int curptr = read_ptr % bufsize;
		int canread = getReadSpace();
		readahead = cnt;
		if (cnt > canread)
			readahead = canread;
		read_ptr += readahead;
		return readahead;
	}else{ //read back
		int canback = canReadBack();
		readahead = cnt;
		if((-cnt)>canback){
			readahead = (-canback);
		}
		read_ptr += readahead;
		return readahead;
	}
}


int NativeBuffer::canReadBack(){
	int can_back = (read_ptr%bufsize) - (write_ptr%bufsize);
	if(can_back < 0) can_back += bufsize;
	return can_back;
}

void NativeBuffer::Reset(){
	read_ptr=0;
	write_ptr=0;
	memset(buffer,0,bufsize);
}
