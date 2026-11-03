cmake_minimum_required(VERSION 3.22.1)
project("dmtx_wrapper")

add_library(dmtx STATIC
    libdmtx/dmtx.c
)
target_include_directories(dmtx PUBLIC libdmtx)
target_compile_definitions(dmtx PRIVATE VERSION="0.7.5")

add_library(dmtx_wrapper SHARED libdmtx_wrapper.cpp)
target_link_libraries(dmtx_wrapper dmtx android log)
