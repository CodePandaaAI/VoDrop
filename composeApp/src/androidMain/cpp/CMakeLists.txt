cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper-cpp-source")

# Extract whisper.cpp version from main CMakeLists.txt (like official example)
file(READ "${WHISPER_CPP_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()
message(STATUS "Whisper version: ${WHISPER_VERSION}")

# Source files - compile whisper.cpp directly
set(SOURCE_FILES
        ${WHISPER_CPP_DIR}/src/whisper.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

# Build the JNI library
add_library(whisper_jni SHARED ${SOURCE_FILES})

# CRITICAL: Define GGML_USE_CPU and WHISPER_VERSION
target_compile_definitions(whisper_jni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper_jni PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

# ARM64 optimizations for better performance
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16)
endif()

# Release build optimizations
if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper_jni PRIVATE -O3)
    target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL)
    target_link_options(whisper_jni PRIVATE -flto)
endif()

# Fetch GGML from whisper.cpp source
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_CPP_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

# Apply same compile options to ggml
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(ggml PRIVATE -march=armv8.2-a+fp16)
endif()

# Link libraries
target_link_libraries(whisper_jni ${LOG_LIB} android ggml)

# Include directories
target_include_directories(whisper_jni PRIVATE
        ${WHISPER_CPP_DIR}
        ${WHISPER_CPP_DIR}/src
        ${WHISPER_CPP_DIR}/include
        ${WHISPER_CPP_DIR}/ggml/include
        ${WHISPER_CPP_DIR}/ggml/src
        ${WHISPER_CPP_DIR}/ggml/src/ggml-cpu
)

# 16KB page alignment for Android 15+
target_link_options(whisper_jni PRIVATE "-Wl,-z,max-page-size=16384")