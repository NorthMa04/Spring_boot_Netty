// gen_and_send_tcp.cpp
#define _CRT_SECURE_NO_WARNINGS
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

#include <iostream>
#include <thread>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <random>
#include <chrono>
#include <ctime>

#pragma comment(lib, "Ws2_32.lib")

// 输出文件
const std::string OUT_PATH = "D:\\testdata.json";

// 服务器地址与端口
const char* SERVER = "127.0.0.1";
const char* SERVER_PORT = "8080";

// 质量枚举对应英文
const std::vector<std::string> QUALITY_NAMES = {
    "Excellent", "Good", "Average", "Fair", "Fail"
};

// 接收线程函数：循环 recv 并打印
void receiveMessages(SOCKET sock) {
    char buffer[4096];
    while (true) {
        int bytes = recv(sock, buffer, sizeof(buffer) - 1, 0);
        if (bytes <= 0) {
            std::cout << "[Receiver] Connection closed or error ("
                << bytes << ")\n";
            break;
        }
        buffer[bytes] = '\0';
        std::cout << "[Receiver] Received ("
            << bytes << " bytes):\n"
            << buffer << std::endl;
    }
}

// 返回当前时间字符串 "YYYY-MM-DD HH:MM:SS"
std::string nowString() {
    std::time_t t = std::time(nullptr);
    std::tm tm;
    localtime_s(&tm, &t);
    char buf[20];
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &tm);
    return buf;
}

int main() {
    // 1. 初始化 Winsock
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        std::cerr << "WSAStartup failed\n";
        return 1;
    }

    // 2. 解析地址
    addrinfo hints = {}, * res = nullptr;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(SERVER, SERVER_PORT, &hints, &res) != 0) {
        std::cerr << "getaddrinfo failed\n";
        WSACleanup();
        return 1;
    }

    // 3. 创建并连接套接字
    SOCKET sock = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (sock == INVALID_SOCKET ||
        connect(sock, res->ai_addr, (int)res->ai_addrlen) == SOCKET_ERROR) {
        std::cerr << "connect failed: " << WSAGetLastError() << "\n";
        freeaddrinfo(res);
        WSACleanup();
        return 1;
    }
    freeaddrinfo(res);
    std::cout << "Connected to " << SERVER << ":" << SERVER_PORT << "\n";

    // 4. 启动接收线程
    std::thread recvThread(receiveMessages, sock);
    recvThread.detach();

    // 随机数生成器
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> distID(100000, 999999);
    std::uniform_int_distribution<> distQ(0, (int)QUALITY_NAMES.size() - 1);

    // 5. 主发送循环
    while (true) {
        // 5.1 生成内层 JSON
        int clientID = distID(gen);
        std::string quality = QUALITY_NAMES[distQ(gen)];
        std::ostringstream inner;
        inner << "{\n"
            << "  \"time\": \"" << nowString() << "\",\n"
            << "  \"clientID\": " << clientID << ",\n"
            << "  \"quality\": \"" << quality << "\"\n"
            << "}";
        std::string innerJson = inner.str();

        // 5.2 包装到外层 "data"
        std::ostringstream wrapper;
        wrapper << "{\n"
            << "  \"data\": " << innerJson << "\n"
            << "}";
        std::string payload = wrapper.str();

        // 5.3 写入本地文件（覆盖）
        {
            std::ofstream ofs(OUT_PATH, std::ios::binary);
            if (ofs) {
                ofs << payload;
                ofs.close();
            }
            else {
                std::cerr << "Failed to write file: " << OUT_PATH << "\n";
            }
        }

        // 5.4 发送到服务器
        int sent = send(sock, payload.c_str(), (int)payload.size(), 0);
        if (sent == SOCKET_ERROR) {
            std::cerr << "send failed: " << WSAGetLastError() << "\n";
            break;
        }
        std::cout << "[Sender] Sent " << sent << " bytes:\n"
            << payload << "\n";

        // 0.5 秒
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
    }

    // 6. 清理
    closesocket(sock);
    WSACleanup();
    return 0;
}

