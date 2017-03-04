#ifndef NATIVEBUFFER_H_
#define NATIVEBUFFER_H_
#ifdef __cplusplus
extern "C"
{
#endif

#include <sys/types.h>
#include <stdlib.h>
#include <string.h>

class NativeBuffer{
private:
	char * buffer;
	size_t	bufsize;
	volatile size_t write_ptr;
	volatile size_t read_ptr;
public:
	NativeBuffer(char *buf,size_t size);
	~NativeBuffer();
	size_t getReadSpace();
	size_t getWriteSpace();
	void setWritePos(size_t pos);
	size_t Read( char *dest, size_t cnt);
	int ReadAhead(int cnt);
	int canReadBack();
	void Reset();
};










#ifdef __cplusplus
}
#endif


#endif
