add_executable(swift-wala-translator-standalone main.cpp)
install(TARGETS swift-wala-translator-standalone DESTINATION bin)
target_link_libraries(swift-wala-translator-standalone 
        swiftWala
        cast
        ${JNI_LIBRARIES})
