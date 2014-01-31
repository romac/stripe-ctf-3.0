
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <openssl/sha.h>

#pragma GCC diagnostic ignored "-Wdeprecated-declarations"

#define BUF_SIZE 1024

char * readStdin(void);
char * gitSha(char * sha, const char * content, const uint64_t counter);

int main(int argc, char const *argv[])
{
  if(argc != 2) {
      printf("Usage: %s difficulty\n", argv[0]);
      return -1;
  }

  const char * difficulty = argv[1];
  const char * content = readStdin();

  char sha[SHA_DIGEST_LENGTH * 2];

  uint64_t counter = 0;

  while(1) {
    counter += 1;

    char * commit = gitSha(sha, content, counter);

    if(strcmp(sha, difficulty) < 0) {
      printf("%s", sha);
      FILE *file = fopen("gominer.txt", "w");
      fputs(commit, file);
      fclose(file);
      exit(0);
    }
  }

  return 0;
}

inline
char * gitSha(char * sha, const char * content, const uint64_t counter)
{
  unsigned char temp[SHA_DIGEST_LENGTH];
  char * content2 = malloc(sizeof(char) * strlen(content) + 50);
  sprintf(content2, "%s\n\n%llx", content, counter);

  size_t len = strlen(content2);

  char * commit = malloc(sizeof(char) * len + 50);
  sprintf(commit, "commit %zd:", len);

  strcat(commit, content2);

  memset(sha, 0x0, SHA_DIGEST_LENGTH * 2);
  memset(temp, 0x0, SHA_DIGEST_LENGTH);

  size_t comlen = strlen(commit);

  char * cur = &commit[6];
  while(*cur != ':') {
    cur++;
  }

  *cur = '\x00';

  SHA1((unsigned char *)commit, comlen, temp);

  for(int i = 0; i < SHA_DIGEST_LENGTH; i++) {
    sprintf((char*)&(sha[i * 2]), "%02x", temp[i]);
  }

  return content2;
}

inline
char * readStdin(void)
{
  char buffer[BUF_SIZE];
  size_t contentSize = 0;
  char * content = malloc(sizeof(char) * BUF_SIZE);

  while(fgets(buffer, BUF_SIZE, stdin))
  {
      char *old = content;
      contentSize += strlen(buffer);
      content = realloc(content, contentSize);
      if(content == NULL)
      {
          perror("Failed to reallocate content");
          free(old);
          exit(2);
      }
      strcat(content, buffer);
  }

  if(ferror(stdin))
  {
      free(content);
      perror("Error reading from stdin.");
      exit(3);
  }

  return content;
}
