#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <direct.h>
#include <errno.h>
#include <string.h>
#define MAX_PATH 100
#define OUT_DIR  "D:\\"
#define OUT_FILE "testdata.json"

enum Quality { EXCELLENT, GOOD, AVERAGE, FAIR, FAIL };

static const char* qualityNames[] = {
    "Excellent",
    "Good",
    "Average",
    "Fair",
    "Fail"
};

int main(void) {
    char fullpath[MAX_PATH];
    snprintf(fullpath, sizeof(fullpath), "%s%s", OUT_DIR, OUT_FILE);

    /* 初始化随机数种子并生成 ClientID */
    srand((unsigned)time(NULL));
    int clientID = rand() % 900000 + 100000;

    /* 随机选取质量等级 */
    int q = rand() % (sizeof(qualityNames) / sizeof(qualityNames[0]));

    /* 获取当前系统时间 */
    time_t t = time(NULL);
    struct tm tm_info;
    localtime_s(&tm_info, &t);
    char datetime[20];
    strftime(datetime, sizeof(datetime), "%Y-%m-%d %H:%M:%S", &tm_info);

    /* 写入 JSON 文件 */
    FILE* fp = fopen(fullpath, "w");
    if (!fp) {
        fprintf(stderr, "Cannot open file %s: %s\n", fullpath, strerror(errno));
        return EXIT_FAILURE;
    }

    fprintf(fp,
        "{\n"
        "  \"time\": \"%s\",\n"
        "  \"clientID\": %d,\n"
        "  \"quality\": \"%s\"\n"
        "}\n",
        datetime,
        clientID,
        qualityNames[q]
    );

    fclose(fp);
    return EXIT_SUCCESS;
}
