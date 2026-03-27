cmake_minimum_required(VERSION 3.22.1)
project("rvkbridge" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(rvkbridge SHARED
    rvk_bridge.cpp
    command_executor.cpp
    code_analyzer.cpp
)

find_library(log-lib log)
target_link_libraries(rvkbridge ${log-lib})
