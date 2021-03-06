#define LOG_TAG "OpenSLTest"
#define DGB 1
#include <jni.h>
#include <pthread.h>
#include "log.h"

#include "com_pzhao_slave_SlavePlay.h"
#include "NativeSlave.h"
#include "NativeBuffer.h"

int startToPlayCount=64;//32;
int frameCount;
void* buffer;
long bufferSize;
volatile bool firstFlag;
volatile bool isPause=false;
NativeSlave *mSlave;
NativeBuffer *mNativeBuffer;

JavaVM *g_jvm = NULL;
jobject g_obj = NULL;


struct timeval start,end;
unsigned  long diff=0;

void *sendStartUdp(void *arg) {
	JNIEnv *env;
	jclass cls;
	jmethodID mid;

	if(isPause){
		gettimeofday(&end, NULL);
		diff=1000*(end.tv_sec-start.tv_sec)+(end.tv_usec-start.tv_usec)/1000;
		ALOGD("pzhao-->pause time:%ldms and sleep 20000",diff);
		usleep(10000);
	}
	else{
		ALOGD("pzhao-->sleep begin (first) 150000");
		usleep(150000);
	}
	ALOGD("pzhao-->sleep end");
	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
		ALOGD("pzhao-->AttachCurrentThread fail");
		return NULL;
	}
	cls = env->GetObjectClass(g_obj);
	if (cls == NULL) {
		ALOGD("pzhao-->find class error");
		goto error;
	}
	mid = env->GetMethodID(cls, "fromJni", "(I)V");
	if (mid == NULL) {
		ALOGD("pzhao-->find method error");
		goto error;
	}

	env->CallVoidMethod(g_obj, mid, 4);

	error: if (g_jvm->DetachCurrentThread() != JNI_OK)
		ALOGD("pzhao-->DetachCurrentThread fail");
	pthread_exit(0);
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_setJniEnv(
		JNIEnv *env, jobject obj) {
	env->GetJavaVM(&g_jvm);
	g_obj = env->NewGlobalRef(obj);
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_createEngine
  (JNIEnv *env, jclass clazz, jobject jbuffer, jint count) {
//	mSlave = new NativeSlave(slaveBufferSize);
	buffer = env->GetDirectBufferAddress(jbuffer);
	bufferSize = env->GetDirectBufferCapacity(jbuffer);
	frameCount=count;
	ALOGD("pzhao->set buffersize %ld frameCount%d",bufferSize,frameCount);
	mNativeBuffer=new NativeBuffer((char*)buffer,bufferSize);
	mSlave=new NativeSlave(mNativeBuffer,frameCount);
	firstFlag = true;
	mSlave->createEngine();

}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_createAudioPlayer(
		JNIEnv *env, jclass clazz) {
	mSlave->createAudioPlayer(mSlave);
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_setPlayingUriAudioPlayer(
		JNIEnv *env, jclass clazz, jboolean isPlaying) {
	if(isPlaying){
		firstFlag=true;
		mSlave->pause=true;
		mSlave->setPlayAudioPlayer(true);
		mSlave->startPlay();
	//	struct  timeval  setplaytrue;
	//	gettimeofday(&setplaytrue,NULL);
	//	ALOGE("pzhao->setplaytrue :%fms",(setplaytrue.tv_sec*1000000+setplaytrue.tv_usec)/1000.0);
	}
	else{
		isPause=true;
		gettimeofday(&start,NULL );
		mSlave->setPlayAudioPlayer(isPlaying);
		mSlave->haswrite=0;
		mSlave->clearQueueBuffer();
		mNativeBuffer->Reset();
		ALOGE("pzhao->setplayfalse");
	}

}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_setMuteUriAudioPlayer(
		JNIEnv *env, jclass clazz, jboolean isMute) {
//	mSlave->setMuteUriAudioPlayer(isMute);
	ALOGE("pzhao->Slave has write %d frames(480)",mSlave->haswrite);
}

JNIEXPORT jboolean JNICALL Java_com_pzhao_slave_SlavePlay_checkWrite(
		JNIEnv *env, jclass clazz) {
//	return mSlave->mBuffer->getWriteSpace() >= 480;
	return mNativeBuffer->getWriteSpace()>=frameCount;
}



JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_setWritePos
  (JNIEnv *env, jclass clazz, jint pos){
	if(mNativeBuffer==NULL)
		return;
	mNativeBuffer->setWritePos(pos);
	if (firstFlag && mNativeBuffer->getReadSpace() >= startToPlayCount*frameCount) {
			firstFlag = false;
			pthread_t pt;
			pthread_create(&pt, NULL, sendStartUdp, NULL);
	//		struct  timeval    startplay;
		//	gettimeofday(&startplay,NULL);
		//	ALOGE("pzhao->starplay time:%fms",(startplay.tv_sec*1000000+startplay.tv_usec)/1000.0);
			//mSlave->pause=false;
		}
}

JNIEXPORT jint JNICALL Java_com_pzhao_slave_SlavePlay_haswrite
  (JNIEnv *env, jclass clazz){
	if(mSlave!=NULL)
		return mSlave->haswrite;
	return 0;
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_shutdown(
		JNIEnv *env, jclass clazz) {
	if (mSlave != NULL)
		delete mSlave;
	// if(buffer!=NULL){
	// 	free(buffer);
	// 	buffer=NULL;
	// }
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_readAhead
  (JNIEnv *env, jclass clazz, jint readahead){
	if(mNativeBuffer==NULL)
		return;
	mSlave->readaheadflag = true;
	mSlave->readaheadcount = readahead;
}

JNIEXPORT void JNICALL Java_com_pzhao_slave_SlavePlay_setPlaybackStat(
		JNIEnv *env, jclass clazz, jboolean stat) {
	mSlave->pause = stat;
}
