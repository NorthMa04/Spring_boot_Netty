#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <direct.h>    // _mkdir
#include <errno.h>
#include <string.h>
#define MAX_PATH 260
enum Quality { QIHAO, LIANGHAO, YIBAN, PUTONG, BUJIGE };

static const char* qualityNames[] = {
    "����",
    "����",
    "һ��",
    "��ͨ",
    "������"
};

int main(void) {
    const char* outDir = "D:\\";
    const char* outFile = "testdata.json";
    char fullpath[MAX_PATH];

    /* ���Ŀ��Ŀ¼�����ڣ���ȡ��ע���������н��д��� */
    // if (_mkdir(outDir) != 0 && errno != EEXIST) {
    //     fprintf(stderr, "�޷�����Ŀ¼ %s: %s\n", outDir, strerror(errno));
    //     return EXIT_FAILURE;
    // }

    /* ƴ������·�� */
    snprintf(fullpath, sizeof(fullpath), "%s%s", outDir, outFile);

    /* ��ʼ��������� */
    srand((unsigned)time(NULL));

    /* ���� 100000�C999999 ����� ClientID */
    int clientID = rand() % 900000 + 100000;

    /* ���ѡȡ�����ȼ� */
    int q = rand() % (sizeof(qualityNames) / sizeof(qualityNames[0]));

    /* ��ȡ����ʽ����ǰʱ�� */
    time_t t = time(NULL);
    struct tm tm_info;
    localtime_s(&tm_info, &t);
    char datetime[20];
    strftime(datetime, sizeof(datetime), "%Y-%m-%d %H:%M:%S", &tm_info);

    /* д�� JSON �ļ� */
    FILE* fp = fopen(fullpath, "w");
    if (!fp) {
        fprintf(stderr, "�޷����ļ� %s: %s\n", fullpath, strerror(errno));
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
