import os, glob, sys, subprocess

# This script compiles every .swift in the given directories and counts the 
# number of SIL instructions in the SIL output.
# If the execution stalls, you might have to press Ctrl+C.

# Usage: instruction_counter.py dir1 dir2 ...
# where dir1, dir2, etc. are directories to be analyzed

if len(sys.argv[1:]) == 0:
    print("Usage: instruction_counter.py dir1 dir2 ... where dir1, dir2, etc. are directories to be analyzed")
    exit(1)
    
instrs =   ["alloc_stack", 
            "alloc_ref", 
            "alloc_ref_dynamic", 
            "alloc_box", 
            "alloc_value_buffer", 
            "alloc_global", 
            "dealloc_stack", 
            "dealloc_box", 
            "project_box", 
            "dealloc_ref", 
            "dealloc_partial_ref", 
            "dealloc_value_buffer", 
            "project_value_buffer", 
            "debug_value", 
            "debug_value_addr", 
            "load", 
            "store",
            "store_borrow",
            "load_borrow",
            "begin_borrow",
            "end_borrow", 
            "assign", 
            "assign_by_wrapper", 
            "mark_uninitialized", 
            "mark_function_escape", 
            "mark_uninitialized_behavior", 
            "copy_addr", 
            "destroy_addr", 
            "index_addr", 
            "tail_addr", 
            "index_raw_pointer", 
            "bind_memory", 
            "begin_access", 
            "end_access", 
            "begin_unpaired_access", 
            "end_unpaired_access",
            "strong_retain",
            "strong_release",
            "set_deallocating",
            "strong_retain_onwoned",
            "unowned_retain",
            "unowned_release",
            "load_weak",
            "store_weak",
            "load_unowned",
            "store_unowned",
            "fix_lifetime",
            "end_lifetime",
            "mark_dependence",
            "is_unique",
            "is_escaping_closure",
            "copy_block",
            "copy_block_without_escaping",
            "function_ref",
            "dynamic_function_ref",
            "prev_dynamic_function_ref",
            "global_addr",
            "global_value",
            "integer_literal",
            "float_literal",
            "string_literal",
            "class_method",
            "objc_method",
            "super_method",
            "objc_super_method",
            "witness_method",
            "apply",
            "begin_apply",
            "abort_apply",
            "end_apply",
            "partial_apply",
            "builtin",
            "metatype",
            "value_metatype",
            "existential_metatype",
            "objc_protocol",
            "retain_value",
            "retain_value_addr",
            "unmanaged_retain_value",
            "copy_value",
            "release_value",
            "release_value_addr",
            "unmanaged_release_value",
            "destroy_value",
            "autorelease_value",
            "tuple",
            "tuple_extract",
            "destructure_tuple",
            "struct",
            "struct_extract",
            "struct_element_addr",
            "destructure_struct",
            "object",
            "ref_element_addr",
            "ret_tail_addr",
            "enum",
            "unchecked_enum_data",
            "init_enum_data_addr",
            "inject_enum_addr",
            "unchecked_take_enum_data_addr",
            "select_enum",
            "select_enum_addr",
            "init_existential_addr",
            "init_existential_value",
            "deinit_existential_addr",
            "deinit_existential_value",
            "open_existential_addr",
            "open_existential_value",
            "init_existential_ref",
            "open_existential_ref",
            "init_existential_metatype",
            "open_existential_metatype",
            "alloc_existential_box",
            "project_existential_box",
            "open_existential_box",
            "open_existential_box_value",
            "dealloc_existential_box",
            "project_block_storage",
            "init_block_storage_header",
            "upcast",
            "address_to_pointer",
            "pointer_to_address",
            "unchecked_ref_cast",
            "unchecked_ref_cast_addr",
            "unchecked_addr_cast",
            "unchecked_trivial_bit_cast",
            "unchecked_bitwise_cast",
            "unchecked_ownership_conversion",
            "ref_to_raw_pointer",
            "raw_pointer_to_ref",
            "ref_to_unowned",
            "unowned_to_ref",
            "ref_to_unmanaged",
            "unmanaged_to_ref",
            "convert_function",
            "convert_escape_to_noescape",
            "thin_function_to_pointer",
            "classify_bridge_object",
            "value_to_bridge_object",
            "ref_to_bridge_object",
            "bridge_object_to_ref",
            "bridge_object_to_word",
            "thin_to_thick_function",
            "thick_to_objc_metatype",
            "objc_metatype_to_object",
            "objc_existential_metatype_to_object",
            "unconditional_checked_cast",
            "unconditional_checked_cast_addr",
            "unconditional_checked_cast_value",
            "cond_fail",
            "unreachable",
            "return",
            "throw",
            "yield",
            "unwind",
            "br",
            "cond_br",
            "switch_value",
            "select_value",
            "switch_enum",
            "switch_enum_addr",
            "dynamic_method_br",
            "checked_cast_br",
            "checked_cast_value_br",
            "checked_cast_addr_br",
            "try_apply"]

instrCounts = {}

for instr in instrs:
    instrCounts[instr] = 0

for directory in sys.argv[1:]:
    for root, dirs, files in os.walk(directory):
        for file in files:
            try:
                if file.endswith(".swift"):
                    filePath = os.path.join(root, file)
                    swiftcCmd = "swiftc " + filePath + " -emit-silgen -Onone "
                    sil = subprocess.getstatusoutput(swiftcCmd)[1]
                    f = open(filePath, "r", errors="ignore")
                    for line in sil.splitlines():
                        line = line.split(':', 1)[0]
                        line = line.split(" ")
                        if line[0] == "//":
                            continue
                        found = False
                        for s in instrs:
                            if s in line:
                                found = s
                                break
                        if (found != False):
                            instrCounts[found] = instrCounts[found] + 1
            except:
                pass
        
nonZero = 0
for instr in instrCounts:
    print (instr + ": " + str(instrCounts[instr]))
    if instrCounts[instr] != 0:
        nonZero = nonZero + 1

print("coverage: " + str(nonZero) + "/" + str(len(instrCounts)) + " or " + "{0:.0%}".format(nonZero/len(instrCounts)))