cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to the whisper.cpp source
set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper-cpp-source")

# Disable features we don't need for Android
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Use the whisper.cpp's own CMake build system
add_subdirectory(${WHISPER_CPP_DIR} whisper_build)

# Create the JNI shared library
add_library(whisper_jni SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
        ${WHISPER_CPP_DIR}/include
        ${WHISPER_CPP_DIR}/ggml/include
)

# Link against whisper (which includes ggml internally now)
find_library(log-lib log)
target_link_libraries(whisper_jni PRIVATE whisper ${log-lib})

# -------------------------------------------
# 16KB Page Alignment for Android 15+ (2025)
# -------------------------------------------
target_link_options(whisper_jni PRIVATE
        "-Wl,-z,max-page-size=16384"
)