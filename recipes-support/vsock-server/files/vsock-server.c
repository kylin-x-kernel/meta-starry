/*
 * vsock-server.c - StarryOS vsock 命令执行服务
 * 
 * 功能：
 * 1. 监听 vsock 端口 5555
 * 2. 接收 OEQA 发送的命令
 * 3. 执行命令并返回输出和退出码
 * 
 * 协议：
 * - 宿主机发送: "命令\n"
 * - 服务返回: "输出内容\nEXIT_CODE: N\n"
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <linux/vm_sockets.h>

#define VSOCK_PORT 5555
#define BUFFER_SIZE 4096

/* 执行命令并捕获输出 */
int execute_command(const char *command, char *output, size_t output_size) {
    FILE *fp;
    char buffer[1024];
    size_t offset = 0;
    int exit_code = 0;
    
    /* 使用 popen 执行命令 */
    fp = popen(command, "r");
    if (fp == NULL) {
        snprintf(output, output_size, "ERROR: Failed to execute command\n");
        return 127;
    }
    
    /* 读取输出 */
    while (fgets(buffer, sizeof(buffer), fp) != NULL && offset < output_size - 1) {
        size_t len = strlen(buffer);
        if (offset + len < output_size) {
            memcpy(output + offset, buffer, len);
            offset += len;
        }
    }
    output[offset] = '\0';
    
    /* 获取退出码 */
    exit_code = pclose(fp);
    if (WIFEXITED(exit_code)) {
        exit_code = WEXITSTATUS(exit_code);
    } else {
        exit_code = 127;
    }
    
    return exit_code;
}

/* 处理客户端连接（短连接模式：每次连接只处理一个命令）*/
void handle_client(int client_fd) {
    char recv_buffer[BUFFER_SIZE];
    char output_buffer[BUFFER_SIZE * 4];
    char response[BUFFER_SIZE * 4 + 64];
    ssize_t n;
    int exit_code;
    
    printf("[vsock-server] Client connected\n");
    
    /* 接收命令 */
    memset(recv_buffer, 0, sizeof(recv_buffer));
    n = recv(client_fd, recv_buffer, sizeof(recv_buffer) - 1, 0);
    
    if (n <= 0) {
        if (n < 0) {
            perror("[vsock-server] recv failed");
        } else {
            printf("[vsock-server] Client disconnected (no command)\n");
        }
        close(client_fd);
        return;
    }
    
    recv_buffer[n] = '\0';
    
    /* 移除换行符 */
    char *newline = strchr(recv_buffer, '\n');
    if (newline) {
        *newline = '\0';
    }
    
    /* 处理特殊命令 */
    if (strcmp(recv_buffer, "QUIT") == 0 || strcmp(recv_buffer, "EXIT") == 0) {
        const char *msg = "OK\nEXIT_CODE: 0\n";
        send(client_fd, msg, strlen(msg), 0);
        close(client_fd);
        return;
    }
    
    if (strlen(recv_buffer) == 0) {
        const char *msg = "EXIT_CODE: 0\n";
        send(client_fd, msg, strlen(msg), 0);
        close(client_fd);
        return;
    }
    
    printf("[vsock-server] Executing: %s\n", recv_buffer);
    
    /* 执行命令 */
    memset(output_buffer, 0, sizeof(output_buffer));
    exit_code = execute_command(recv_buffer, output_buffer, sizeof(output_buffer));
    
    /* 构造响应（输出 + 退出码）*/
    snprintf(response, sizeof(response), "%sEXIT_CODE: %d\n", 
             output_buffer, exit_code);
    
    /* 发送响应 */
    size_t total_sent = 0;
    size_t response_len = strlen(response);
    while (total_sent < response_len) {
        ssize_t sent = send(client_fd, response + total_sent, 
                           response_len - total_sent, 0);
        if (sent < 0) {
            perror("[vsock-server] send failed");
            break;
        }
        total_sent += sent;
    }
    
    printf("[vsock-server] Command completed (exit code: %d)\n", exit_code);
    
    /* 短连接模式：处理完一个命令后关闭连接 */
    close(client_fd);
}

int main(int argc, char *argv[]) {
    int server_fd, client_fd;
    struct sockaddr_vm server_addr, client_addr;
    socklen_t client_len;
    int opt = 1;
    
    printf("===========================================\n");
    printf("  StarryOS vsock Command Execution Server\n");
    printf("===========================================\n");
    printf("Listening on CID=ANY PORT=%d\n", VSOCK_PORT);
    printf("Protocol: Send 'command\\n', receive 'output\\nEXIT_CODE: N\\n'\n");
    printf("===========================================\n\n");
    
    /* 创建 vsock socket */
    server_fd = socket(AF_VSOCK, SOCK_STREAM, 0);
    if (server_fd < 0) {
        perror("[vsock-server] socket() failed");
        return 1;
    }
    
    /* 设置 socket 选项 */
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        perror("[vsock-server] setsockopt() failed");
        close(server_fd);
        return 1;
    }
    
    /* 绑定地址 */
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.svm_family = AF_VSOCK;
    server_addr.svm_cid = VMADDR_CID_ANY;  /* 接受任何 CID 的连接 */
    server_addr.svm_port = VSOCK_PORT;
    
    if (bind(server_fd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("[vsock-server] bind() failed");
        close(server_fd);
        return 1;
    }
    
    /* 开始监听 */
    if (listen(server_fd, 5) < 0) {
        perror("[vsock-server] listen() failed");
        close(server_fd);
        return 1;
    }
    
    printf("[vsock-server] Ready to accept connections\n\n");
    
    /* 接受连接循环 */
    while (1) {
        client_len = sizeof(client_addr);
        client_fd = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);
        
        if (client_fd < 0) {
            perror("[vsock-server] accept() failed");
            continue;
        }
        
        printf("[vsock-server] Connection from CID=%u PORT=%u\n",
               client_addr.svm_cid, client_addr.svm_port);
        
        /* 处理客户端（单线程，同步处理）*/
        handle_client(client_fd);
    }
    
    close(server_fd);
    return 0;
}

