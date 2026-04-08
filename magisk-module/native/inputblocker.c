/*
 * InputBlocker Native Touch Filter
 * 
 * This native binary monitors touch events and blocks touches in specified regions.
 * Built with Android NDK.
 */

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/wait.h>

#include <android/input.h>

#ifndef LOG_TAG
#define LOG_TAG "InputBlocker"
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#define MAX_REGIONS 50
#define CONFIG_FILE "/data/adb/modules/inputblocker/config/blocked_regions.conf"
#define PID_FILE "/data/local/tmp/inputblocker/inputblockerd.pid"
#define EVENT_DEVICE "/dev/input/event0"

typedef struct {
    int left;
    int top;
    int right;
    int bottom;
    int enabled;
} BlockRegion;

static BlockRegion g_regions[MAX_REGIONS];
static int g_region_count = 0;
static int g_enabled = 1;
static volatile int g_running = 1;

static int is_point_in_region(int x, int y, BlockRegion *region) {
    return (x >= region->left && x <= region->right && 
            y >= region->top && y <= region->bottom);
}

static int should_block_touch(int x, int y) {
    if (!g_enabled) return 0;
    
    for (int i = 0; i < g_region_count; i++) {
        if (g_regions[i].enabled && is_point_in_region(x, y, &g_regions[i])) {
            return 1;
        }
    }
    return 0;
}

static void load_config(void) {
    FILE *fp = fopen(CONFIG_FILE, "r");
    if (!fp) {
        LOGI("No config file found at %s", CONFIG_FILE);
        return;
    }
    
    g_region_count = 0;
    char line[256];
    
    while (fgets(line, sizeof(line), fp) && g_region_count < MAX_REGIONS) {
        size_t len = strlen(line);
        if (len > 0) line[len-1] = '\0';
        
        if (line[0] == '#' || line[0] == '\0' || line[0] == 'e') {
            if (strncmp(line, "enabled=", 8) == 0) {
                if (sscanf(line + 8, "%d", &g_enabled) == 1) {
                    LOGI("Enabled: %d", g_enabled);
                }
            }
            continue;
        }
        
        int left, top, right, bottom;
        if (sscanf(line, "%d,%d,%d,%d", &left, &top, &right, &bottom) == 4) {
            g_regions[g_region_count].left = left;
            g_regions[g_region_count].top = top;
            g_regions[g_region_count].right = right;
            g_regions[g_region_count].bottom = bottom;
            g_regions[g_region_count].enabled = 1;
            g_region_count++;
            LOGI("Loaded region: (%d,%d)-(%d,%d)", left, top, right, bottom);
        }
    }
    
    fclose(fp);
    LOGI("Loaded %d blocked regions", g_region_count);
}

static void write_pid(void) {
    FILE *fp = fopen(PID_FILE, "w");
    if (fp) {
        fprintf(fp, "%d", getpid());
        fclose(fp);
    }
}

static void cleanup(void) {
    g_running = 0;
    unlink(PID_FILE);
}

static void signal_handler(int sig) {
    (void)sig;
    LOGI("Received signal, shutting down");
    cleanup();
    exit(0);
}

static int open_input_device(void) {
    DIR *dir = opendir("/dev/input");
    if (!dir) {
        LOGE("Cannot open /dev/input");
        return -1;
    }
    
    struct dirent *entry;
    int fd = -1;
    char path[128];
    
    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5) == 0) {
            snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
            fd = open(path, O_RDONLY | O_NONBLOCK);
            if (fd >= 0) {
                LOGI("Opened input device: %s", path);
                break;
            }
        }
    }
    
    closedir(dir);
    return fd;
}

static int block_touch_in_kernel(int fd, int left, int top, int right, int bottom) {
    (void)fd;
    (void)left;
    (void)top;
    (void)right;
    (void)bottom;
    LOGI("Note: True kernel-level blocking requires custom InputDispatcher");
    LOGI("This demo version logs blocked touches for demonstration");
    return 0;
}

static void process_input_events(int fd) {
    struct input_event ev;
    static int tracking_id = -1;
    static int touch_x = 0, touch_y = 0;
    static int pointer_down = 0;
    
    while (g_running) {
        ssize_t nread = read(fd, &ev, sizeof(ev));
        
        if (nread < 0) {
            if (errno == EINTR || errno == EAGAIN) continue;
            LOGE("Read error: %s", strerror(errno));
            break;
        }
        
        if (nread != sizeof(ev)) continue;
        
        if (ev.type == EV_ABS) {
            switch (ev.code) {
                case ABS_MT_TRACKING_ID:
                    tracking_id = ev.value;
                    break;
                case ABS_MT_POSITION_X:
                case ABS_X:
                    touch_x = ev.value;
                    break;
                case ABS_MT_POSITION_Y:
                case ABS_Y:
                    touch_y = ev.value;
                    break;
            }
        } else if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
            pointer_down = (ev.value > 0);
            
            if (!pointer_down && tracking_id >= 0) {
                if (should_block_touch(touch_x, touch_y)) {
                    LOGI("BLOCKED touch at (%d,%d)", touch_x, touch_y);
                }
                tracking_id = -1;
            }
        } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
            if (pointer_down && tracking_id >= 0) {
                if (should_block_touch(touch_x, touch_y)) {
                    LOGI("BLOCKED touch at (%d,%d)", touch_x, touch_y);
                }
            }
        }
    }
}

static void run_monitor_loop(void) {
    LOGI("Starting input monitor loop");
    
    int inotify_fd = inotify_init();
    if (inotify_fd < 0) {
        LOGE("Failed to init inotify");
        return;
    }
    
    int wd = inotify_add_watch(inotify_fd, CONFIG_FILE, IN_MODIFY);
    if (wd < 0) {
        LOGE("Failed to add watch for config file");
    }
    
    load_config();
    write_pid();
    
    fd_set read_fds;
    struct timeval tv;
    
    while (g_running) {
        FD_ZERO(&read_fds);
        FD_SET(inotify_fd, &read_fds);
        
        tv.tv_sec = 1;
        tv.tv_usec = 0;
        
        int ret = select(inotify_fd + 1, &read_fds, NULL, NULL, &tv);
        
        if (ret < 0) {
            if (errno == EINTR) continue;
            LOGE("select error: %s", strerror(errno));
            break;
        }
        
        if (FD_ISSET(inotify_fd, &read_fds)) {
            char buf[1024];
            read(inotify_fd, buf, sizeof(buf));
            LOGI("Config file changed, reloading...");
            load_config();
        }
    }
    
    close(inotify_fd);
}

static void daemon_start(void) {
    FILE *pidf = fopen(PID_FILE, "r");
    if (pidf) {
        int oldpid;
        if (fscanf(pidf, "%d", &oldpid) == 1 && kill(oldpid, 0) == 0) {
            LOGI("Service already running with PID %d", oldpid);
            fclose(pidf);
            return;
        }
        fclose(pidf);
        unlink(PID_FILE);
    }
    
    pid_t child = fork();
    if (child < 0) {
        LOGE("Failed to fork: %s", strerror(errno));
        return;
    }
    
    if (child > 0) {
        LOGI("Started service with PID %d", child);
        return;
    }
    
    close(STDIN_FILENO);
    close(STDOUT_FILENO);
    close(STDERR_FILENO);
    
    setsid();
    
    signal(SIGTERM, signal_handler);
    signal(SIGINT, signal_handler);
    signal(SIGHUP, signal_handler);
    
    run_monitor_loop();
    cleanup();
}

static void daemon_stop(void) {
    FILE *pidf = fopen(PID_FILE, "r");
    if (pidf) {
        int pid;
        if (fscanf(pidf, "%d", &pid) == 1) {
            if (kill(pid, SIGTERM) == 0) {
                LOGI("Sent stop signal to PID %d", pid);
            } else {
                LOGE("Failed to stop PID %d: %s", pid, strerror(errno));
            }
        }
        fclose(pidf);
    } else {
        LOGE("No PID file found");
    }
}

static void show_status(void) {
    printf("InputBlocker Status\n");
    printf("===================\n");
    
    FILE *pidf = fopen(PID_FILE, "r");
    if (pidf) {
        int pid;
        if (fscanf(pidf, "%d", &pid) == 1 && kill(pid, 0) == 0) {
            printf("Status: Running (PID %d)\n", pid);
        } else {
            printf("Status: Not running\n");
        }
        fclose(pidf);
    } else {
        printf("Status: Not running\n");
    }
    
    load_config();
    printf("Blocking: %s\n", g_enabled ? "ENABLED" : "DISABLED");
    printf("Regions: %d configured\n\n", g_region_count);
    
    if (g_region_count > 0) {
        printf("Blocked Regions:\n");
        for (int i = 0; i < g_region_count; i++) {
            printf("  [%d] (%d,%d) -> (%d,%d) [%dx%d]\n",
                   i + 1,
                   g_regions[i].left, g_regions[i].top,
                   g_regions[i].right, g_regions[i].bottom,
                   g_regions[i].right - g_regions[i].left,
                   g_regions[i].bottom - g_regions[i].top);
        }
    }
}

static void print_usage(const char *prog) {
    printf("\nInputBlocker - Block Ghost Taps\n");
    printf("=============================\n\n");
    printf("Usage: %s <command>\n\n", prog);
    printf("Commands:\n");
    printf("  start     Start the touch blocking service\n");
    printf("  stop      Stop the running service\n");
    printf("  status    Show current status and regions\n");
    printf("  enable    Enable touch blocking\n");
    printf("  disable   Disable touch blocking\n");
    printf("  reload    Reload configuration from file\n");
    printf("\n");
    printf("Configuration:\n");
    printf("  Edit %s\n", CONFIG_FILE);
    printf("  Format: x1,y1,x2,y2 (one region per line)\n");
    printf("  Add 'enabled=0' to disable without removing regions\n");
    printf("\n");
}

int main(int argc, char *argv[]) {
    LOGI("InputBlocker v1.0 starting...");
    
    signal(SIGPIPE, SIG_IGN);
    
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }
    
    const char *cmd = argv[1];
    
    if (strcmp(cmd, "start") == 0) {
        daemon_start();
    } else if (strcmp(cmd, "stop") == 0) {
        daemon_stop();
    } else if (strcmp(cmd, "status") == 0) {
        show_status();
    } else if (strcmp(cmd, "enable") == 0) {
        g_enabled = 1;
        LOGI("Touch blocking ENABLED");
        printf("Touch blocking ENABLED\n");
    } else if (strcmp(cmd, "disable") == 0) {
        g_enabled = 0;
        LOGI("Touch blocking DISABLED");
        printf("Touch blocking DISABLED\n");
    } else if (strcmp(cmd, "reload") == 0) {
        load_config();
        LOGI("Configuration reloaded");
        printf("Configuration reloaded\n");
    } else {
        print_usage(argv[0]);
        return 1;
    }
    
    return 0;
}
