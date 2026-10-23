cmake_minimum_required(VERSION 3.22)
project(whisper_jni CXX C)

# Fire TV sticks run 32-bit Android on ARMv8 cores: plain ARMv7 NEON (the NDK default) has no
# fused multiply-add, which made recognition about 3x slower on the stick.
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4)
endif()
# Debug builds of the app still get an optimized engine: unoptimized ggml is unusably slow.
set(CMAKE_BUILD_TYPE Release)

# One self-contained library: whisper.cpp and ggml linked in statically.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_SRC} whisper EXCLUDE_FROM_ALL)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni whisper android log)
target_link_options(whisper_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
