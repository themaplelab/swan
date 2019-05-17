precondition(WALA_DIR)

add_library(swiftWala SHARED
        WALAInstance.cpp
        SymbolTable.cpp
        BasicBlockLabeller.cpp
        SILWalaInstructionVisitor.cpp)

install(TARGETS swiftWala DESTINATION lib)

target_link_libraries(swiftWala
    swiftFrontendTool
    swiftParse
    swiftSema
    swiftAST
    cast
    edit)

