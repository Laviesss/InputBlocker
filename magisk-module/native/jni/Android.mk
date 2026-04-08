LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := inputblocker
LOCAL_SRC_FILES := inputblocker.c
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS := -Wno-unused-parameter
include $(BUILD_EXECUTABLE)
