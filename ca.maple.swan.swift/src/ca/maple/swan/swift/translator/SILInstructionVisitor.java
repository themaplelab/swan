//===--- SILInstructionVisitor.java --------------------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//

package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;

/*
 * Class for visiting SIL instructions.
 */
public abstract class SILInstructionVisitor<TR, TC> {

    public TR visit(CAstNode N, TC C) {

        switch((String)N.getChild(0).getValue()) {
            case "alloc_stack": return visitAllocStack(N.getChild(2), C); 
            case "alloc_ref": return visitAllocRef(N.getChild(2), C); 
            case "alloc_ref_dynamic": return visitAllocRefDynamic(N.getChild(2), C); 
            case "alloc_box": return visitAllocBox(N.getChild(2), C); 
            case "alloc_value_buffer": return visitAllocValueBuffer(N.getChild(2), C); 
            case "alloc_global": return visitAllocGlobal(N.getChild(2), C); 
            case "dealloc_stack": return visitDeallocStack(N.getChild(2), C); 
            case "dealloc_box": return visitDeallocBox(N.getChild(2), C); 
            case "project_box": return visitProjectBox(N.getChild(2), C); 
            case "dealloc_ref": return visitDeallocRef(N.getChild(2), C); 
            case "dealloc_partial_ref": return visitDeallocPartialRef(N.getChild(2), C); 
            case "dealloc_value_buffer": return visitDeallocValueBuffer(N.getChild(2), C); 
            case "project_value_buffer": return visitProjectValueBuffer(N.getChild(2), C); 

            case "debug_value": return visitDebugValue(N.getChild(2), C); 
            case "debug_value_addr": return visitDebugValueAddr(N.getChild(2), C); 

            case "load": return visitLoad(N.getChild(2), C); 
            case "store": return visitStore(N.getChild(2), C); 
            case "load_borrow": return visitLoadBorrow(N.getChild(2), C);
            case "begin_borrow": return visitBeginBorrow(N.getChild(2), C);
            case "end_borrow": return visitEndBorrow(N.getChild(2), C); 
            case "assign": return visitAssign(N.getChild(2), C); 
            case "assign_by_wrapper": return visitAssignByWrapper(N.getChild(2), C); 
            case "mark_uninitialized": return visitMarkUninitialized(N.getChild(2), C); 
            case "mark_function_escape": return visitMarkFunctionEscape(N.getChild(2), C); 
            case "mark_uninitialized_behavior": return visitMarkUninitializedBehavior(N.getChild(2), C); 
            case "copy_addr": return visitCopyAddr(N.getChild(2), C); 
            case "destroy_addr": return visitDestroyAddr(N.getChild(2), C); 
            case "index_addr": return visitIndexAddr(N.getChild(2), C); 
            case "tail_addr": return visitTailAddr(N.getChild(2), C); 
            case "index_raw_pointer": return visitIndexRawPointer(N.getChild(2), C); 
            case "bind_memory": return visitBindMemory(N.getChild(2), C); 
            case "begin_access": return visitBeginAccess(N.getChild(2), C); 
            case "end_access": return visitEndAccess(N.getChild(2), C); 
            case "begin_unpaired_access": return visitBeginUnpairedAccess(N.getChild(2), C); 
            case "end_unpaired_access": return visitEndUnpairedAccess(N.getChild(2), C);

            case "strong_retain": return visitStrongRetain(N.getChild(2), C);
            case "strong_release": return visitStrongRelease(N.getChild(2), C);
            case "set_deallocating": return visitSetDeallocating(N.getChild(2), C);
            case "strong_retain_onwoned": return visitStrongRetainUnowned(N.getChild(2), C);
            case "unowned_retain": return visitUnownedRetain(N.getChild(2), C);
            case "unowned_release": return visitUnownedRelease(N.getChild(2), C);
            case "load_weak": return visitLoadWeak(N.getChild(2), C);
            case "store_weak": return visitStoreWeak(N.getChild(2), C);
            case "load_unowned": return visitLoadUnowned(N.getChild(2), C);
            case "store_unowned": return visitStoreUnowned(N.getChild(2), C);
            case "fix_lifetime": return visitFixLifetime(N.getChild(2), C);
            case "end_lifetime": return visitEndLifetime(N.getChild(2), C);
            case "mark_dependence": return visitMarkDependence(N.getChild(2), C);
            case "is_unique": return visitIsUnique(N.getChild(2), C);
            case "is_escaping_closure": return visitIsEscapingClosure(N.getChild(2), C);
            case "copy_block": return visitCopyBlock(N.getChild(2), C);
            case "copy_block_without_escaping": return visitCopyBlockWithoutEscaping(N.getChild(2), C);

            case "function_ref": return visitFunctionRef(N.getChild(2), C);
            case "dynamic_function_ref": return visitDynamicFunctionRef(N.getChild(2), C);
            case "prev_dynamic_function_ref": return visitPrevDynamicFunctionRef(N.getChild(2), C);
            case "global_addr": return visitGlobalAddr(N.getChild(2), C);
            case "global_value": return visitGlobalValue(N.getChild(2), C);
            case "integer_literal": return visitIntegerLiteral(N.getChild(2), C);
            case "float_literal": return visitFloatLiteral(N.getChild(2), C);
            case "string_literal": return visitStringLiteral(N.getChild(2), C);

            case "class_method": return visitClassMethod(N.getChild(2), C);
            case "objc_method": return visitObjCMethod(N.getChild(2), C);
            case "super_method": return visitSuperMethod(N.getChild(2), C);
            case "objc_super_method": return visitObjCSuperMethod(N.getChild(2), C);
            case "witness_method": return visitWitnessMethod(N.getChild(2), C);

            case "apply": return visitApply(N.getChild(2), C);
            case "begin_apply": return visitBeginApply(N.getChild(2), C);
            case "abort_apply": return visitAbortApply(N.getChild(2), C);
            case "end_apply": return visitEndApply(N.getChild(2), C);
            case "partial_apply": return visitPartialApply(N.getChild(2), C);
            case "builtin": return visitBuiltin(N.getChild(2), C);

            case "metatype": return visitMetatype(N.getChild(2), C);
            case "value_metatype": return visitValueMetatype(N.getChild(2), C);
            case "existential_metatype": return visitExistentialMetatype(N.getChild(2), C);
            case "objc_protocol": return visitObjCProtocol(N.getChild(2), C);

            case "retain_value": return visitRetainValue(N.getChild(2), C);
            case "retain_value_addr": return visitRetainValueAddr(N.getChild(2), C);
            case "unmanaged_retain_value": return visitUnmanagedRetainValue(N.getChild(2), C);
            case "copy_value": return visitCopyValue(N.getChild(2), C);
            case "release_value": return visitReleaseValue(N.getChild(2), C);
            case "release_value_addr": return visitReleaseValueAddr(N.getChild(2), C);
            case "unmanaged_release_value": return visitUnmanagedReleaseValue(N.getChild(2), C);
            case "destroy_value": return visitDestroyValue(N.getChild(2), C);
            case "autorelease_value": return visitAutoreleaseValue(N.getChild(2), C);
            case "tuple": return visitTuple(N.getChild(2), C);
            case "tuple_extract": return visitTupleExtract(N.getChild(2), C);
            case "destructure_tuple": return visitDestructureTuple(N.getChild(2), C);
            case "struct": return visitStruct(N.getChild(2), C);
            case "struct_extract": return visitStructExtract(N.getChild(2), C);
            case "struct_element_addr": return visitStructElementAddr(N.getChild(2), C);
            case "destructure_struct": return visitDestructureStruct(N.getChild(2), C);
            case "object": return visitObject(N.getChild(2), C);
            case "ref_element_addr": return visitRefElementAddr(N.getChild(2), C);
            case "ret_tail_addr": return visitRefTailAddr(N.getChild(2), C);

            case "enum": return visitEnum(N.getChild(2), C);
            case "unchecked_enum_data": return visitUncheckedEnumData(N.getChild(2), C);
            case "init_enum_data_addr": return visitInitEnumDataAddr(N.getChild(2), C);
            case "inject_enum_addr": return visitInjectEnumAddr(N.getChild(2), C);
            case "unchecked_take_enum_data_addr": return visitUncheckedTakeEnumDataAddr(N.getChild(2), C);
            case "select_enum": return visitSelectEnum(N.getChild(2), C);
            case "select_enum_addr": return visitSelectEnumAddr(N.getChild(2), C);

            case "init_existential_addr": return visitInitExistentialAddr(N.getChild(2), C);
            case "init_existential_value": return visitInitExistentialValue(N.getChild(2), C);
            case "deinit_existential_addr": return visitDeinitExistentialAddr(N.getChild(2), C);
            case "deinit_existential_value": return visitDeinitExistentialValue(N.getChild(2), C);
            case "open_existential_addr": return visitOpenExistentialAddr(N.getChild(2), C);
            case "open_existential_value": return visitOpenExistentialValue(N.getChild(2), C);
            case "init_existential_ref": return visitInitExistentialRef(N.getChild(2), C);
            case "open_existential_ref": return visitOpenExistentialRef(N.getChild(2), C);
            case "init_existential_metatype": return visitInitExistentialMetatype(N.getChild(2), C);
            case "open_existential_metatype": return visitOpenExistentialMetatype(N.getChild(2), C);
            case "alloc_existential_box": return visitAllocExistentialBox(N.getChild(2), C);
            case "project_existential_box": return visitProjectExistentialBox(N.getChild(2), C);
            case "open_existential_box": return visitOpenExistentialBox(N.getChild(2), C);
            case "open_existential_box_value": return visitOpenExistentialBoxValue(N.getChild(2), C);
            case "dealloc_existential_box": return visitDeallocExistentialBox(N.getChild(2), C);

            case "project_block_storage": return visitProjectBlockStorage(N.getChild(2), C);
            case "init_block_storage_header": return visitInitBlockStorageHeader(N.getChild(2), C);

            case "upcast": return visitUpcast(N.getChild(2), C);
            case "address_to_pointer": return visitAddressToPointer(N.getChild(2), C);
            case "pointer_to_address": return visitPointerToAddress(N.getChild(2), C);
            case "unchecked_ref_cast": return visitUncheckedRefCast(N.getChild(2), C);
            case "unchecked_ref_cast_addr": return visitUncheckedRefCastAddr(N.getChild(2), C);
            case "unchecked_addr_cast": return visitUncheckedAddrCast(N.getChild(2), C);
            case "unchecked_trivial_bit_cast": return visitUncheckedTrivialBitCast(N.getChild(2), C);
            case "unchecked_bitwise_cast": return visitUncheckedBitwiseCast(N.getChild(2), C);
            case "unchecked_ownership_conversion": return visitUncheckedOwnershipConversion(N.getChild(2), C);
            case "ref_to_raw_pointer": return visitRefToRawPointer(N.getChild(2), C);
            case "raw_pointer_to_ref": return visitRawPointerToRef(N.getChild(2), C);
            case "ref_to_unowned": return visitRefToUnowned(N.getChild(2), C);
            case "unowned_to_ref": return visitUnownedToRef(N.getChild(2), C);
            case "ref_to_unmanaged": return visitRefToUnmanaged(N.getChild(2), C);
            case "unmanaged_to_ref": return visitUnmanagedToRef(N.getChild(2), C);
            case "convert_function": return visitConvertFunction(N.getChild(2), C);
            case "convert_escape_to_noescape": return visitConvertEscapeToNoEscape(N.getChild(2), C);
            case "thin_function_to_pointer": return visitThinToThickFunction(N.getChild(2), C);
            case "classify_bridge_object": return visitClassifyBridgeObject(N.getChild(2), C);
            case "value_to_bridge_object": return visitValueToBridgeObject(N.getChild(2), C);
            case "ref_to_bridge_object": return visitRefToBridgeObject(N.getChild(2), C);
            case "bridge_object_to_ref": return visitBridgeObjectToRef(N.getChild(2), C);
            case "bridge_object_to_word": return visitBridgeObjectToWord(N.getChild(2), C);
            case "thin_to_thick_function": return visitThinToThickFunction(N.getChild(2), C);
            case "thick_to_objc_metatype": return visitThickToObjCMetatype(N.getChild(2), C);
            case "objc_metatype_to_object": return visitObjCMetatypeToObject(N.getChild(2), C);
            case "objc_existential_metatype_to_object": return visitObjCExistentialMetatypeToObject(N.getChild(2), C);

            case "unconditional_checked_cast": return visitUnconditionalCheckedCast(N.getChild(2), C);
            case "unconditional_checked_cast_addr": return visitUnconditionalCheckedCastAddr(N.getChild(2), C);
            case "unconditional_checked_cast_value": return visitUnconditionalCheckedCastValue(N.getChild(2), C);

            case "cond_fail": return visitCondFail(N.getChild(2), C);

            case "unreachable": return visitUnreachable(N.getChild(2), C);
            case "return": return visitReturn(N.getChild(2), C);
            case "throw": return visitThrow(N.getChild(2), C);
            case "yield": return visitYield(N.getChild(2), C);
            case "unwind": return visitUnwind(N.getChild(2), C);
            case "br": return visitBr(N.getChild(2), C);
            case "cond_br": return visitCondBr(N.getChild(2), C);
            case "switch_value": return visitSwitchValue(N.getChild(2), C);
            case "select_value": return visitSelectValue(N.getChild(2), C);
            case "switch_enum": return visitSwitchEnum(N.getChild(2), C);
            case "switch_enum_addr": return visitSwitchEnumAddr(N.getChild(2), C);
            case "dynamic_method_br": return visitDynamicMethodBr(N.getChild(2), C);
            case "checked_cast_br": return visitCheckedCastBr(N.getChild(2), C);
            case "checked_cast_value_br": return visitCheckedCastValueBr(N.getChild(2), C);
            case "checked_cast_addr_br": return visitCheckedCastAddrBr(N.getChild(2), C);
            case "try_apply": return visitTryApply(N.getChild(2), C);

            default:
                System.err.println("ERROR: UNKNOWN INSTRUCTION: " + (String)N.getValue());
                return null;
        }
    }

    protected abstract CAstSourcePositionMap.Position getInstructionPosition(CAstNode N);

    /* ALLOCATION AND DEALLOCATION */
    protected abstract TR visitAllocStack(CAstNode N, TC C);
    protected abstract TR visitAllocRef(CAstNode N, TC C);
    protected abstract TR visitAllocRefDynamic(CAstNode N, TC C);
    protected abstract TR visitAllocBox(CAstNode N, TC C);
    protected abstract TR visitAllocValueBuffer(CAstNode N, TC C);
    protected abstract TR visitAllocGlobal(CAstNode N, TC C);
    protected abstract TR visitDeallocStack(CAstNode N, TC C);
    protected abstract TR visitDeallocBox(CAstNode N, TC C);
    protected abstract TR visitProjectBox(CAstNode N, TC C);
    protected abstract TR visitDeallocRef(CAstNode N, TC C);
    protected abstract TR visitDeallocPartialRef(CAstNode N, TC C);
    protected abstract TR visitDeallocValueBuffer(CAstNode N, TC C);
    protected abstract TR visitProjectValueBuffer(CAstNode N, TC C);

    /* DEBUG INFORMATION */
    protected abstract TR visitDebugValue(CAstNode N, TC C);
    protected abstract TR visitDebugValueAddr(CAstNode N, TC C);

    /* ACCESSING MEMORY */
    protected abstract TR visitLoad(CAstNode N, TC C);
    protected abstract TR visitStore(CAstNode N, TC C);
    protected abstract TR visitLoadBorrow(CAstNode N, TC C);
    protected abstract TR visitBeginBorrow(CAstNode N, TC C);
    protected abstract TR visitEndBorrow(CAstNode N, TC C);
    protected abstract TR visitAssign(CAstNode N, TC C);
    protected abstract TR visitAssignByWrapper(CAstNode N, TC C);
    protected abstract TR visitMarkUninitialized(CAstNode N, TC C);
    protected abstract TR visitMarkFunctionEscape(CAstNode N, TC C);
    protected abstract TR visitMarkUninitializedBehavior(CAstNode N, TC C);
    protected abstract TR visitCopyAddr(CAstNode N, TC C);
    protected abstract TR visitDestroyAddr(CAstNode N, TC C);
    protected abstract TR visitIndexAddr(CAstNode N, TC C);
    protected abstract TR visitTailAddr(CAstNode N, TC C);
    protected abstract TR visitIndexRawPointer(CAstNode N, TC C);
    protected abstract TR visitBindMemory(CAstNode N, TC C);
    protected abstract TR visitBeginAccess(CAstNode N, TC C);
    protected abstract TR visitEndAccess(CAstNode N, TC C);
    protected abstract TR visitBeginUnpairedAccess(CAstNode N, TC C);
    protected abstract TR visitEndUnpairedAccess(CAstNode N, TC C);

    /* REFERENCE COUNTING */
    protected abstract TR visitStrongRetain(CAstNode N, TC C);
    protected abstract TR visitStrongRelease(CAstNode N, TC C);
    protected abstract TR visitSetDeallocating(CAstNode N, TC C);
    protected abstract TR visitStrongRetainUnowned(CAstNode N, TC C);
    protected abstract TR visitUnownedRetain(CAstNode N, TC C);
    protected abstract TR visitUnownedRelease(CAstNode N, TC C);
    protected abstract TR visitLoadWeak(CAstNode N, TC C);
    protected abstract TR visitStoreWeak(CAstNode N, TC C);
    protected abstract TR visitLoadUnowned(CAstNode N, TC C);
    protected abstract TR visitStoreUnowned(CAstNode N, TC C);
    protected abstract TR visitFixLifetime(CAstNode N, TC C);
    protected abstract TR visitEndLifetime(CAstNode N, TC C);
    protected abstract TR visitMarkDependence(CAstNode N, TC C);
    protected abstract TR visitIsUnique(CAstNode N, TC C);
    protected abstract TR visitIsEscapingClosure(CAstNode N, TC C);
    protected abstract TR visitCopyBlock(CAstNode N, TC C);
    protected abstract TR visitCopyBlockWithoutEscaping(CAstNode N, TC C);
    // TODO: visit builtin "unsafeGuaranteed" and builtin "unsafeGuaranteedEnd" ?

    /* LITERALS */
    protected abstract TR visitFunctionRef(CAstNode N, TC C);
    protected abstract TR visitDynamicFunctionRef(CAstNode N, TC C);
    protected abstract TR visitPrevDynamicFunctionRef(CAstNode N, TC C);
    protected abstract TR visitGlobalAddr(CAstNode N, TC C);
    protected abstract TR visitGlobalValue(CAstNode N, TC C);
    protected abstract TR visitIntegerLiteral(CAstNode N, TC C);
    protected abstract TR visitFloatLiteral(CAstNode N, TC C);
    protected abstract TR visitStringLiteral(CAstNode N, TC C);

    /* DYNAMIC DISPATCH */
    protected abstract TR visitClassMethod(CAstNode N, TC C);
    protected abstract TR visitObjCMethod(CAstNode N, TC C);
    protected abstract TR visitSuperMethod(CAstNode N, TC C);
    protected abstract TR visitObjCSuperMethod(CAstNode N, TC C);
    protected abstract TR visitWitnessMethod(CAstNode N, TC C);

    /* FUNCTION APPLICATION */
    protected abstract TR visitApply(CAstNode N, TC C);
    protected abstract TR visitBeginApply(CAstNode N, TC C);
    protected abstract TR visitAbortApply(CAstNode N, TC C);
    protected abstract TR visitEndApply(CAstNode N, TC C);
    protected abstract TR visitPartialApply(CAstNode N, TC C);
    protected abstract TR visitBuiltin(CAstNode N, TC C);

    /* METATYPES */
    protected abstract TR visitMetatype(CAstNode N, TC C);
    protected abstract TR visitValueMetatype(CAstNode N, TC C);
    protected abstract TR visitExistentialMetatype(CAstNode N, TC C);
    protected abstract TR visitObjCProtocol(CAstNode N, TC C);

    /* AGGREGATE TYPES */
    protected abstract TR visitRetainValue(CAstNode N, TC C);
    protected abstract TR visitRetainValueAddr(CAstNode N, TC C);
    protected abstract TR visitUnmanagedRetainValue(CAstNode N, TC C);
    protected abstract TR visitCopyValue(CAstNode N, TC C);
    protected abstract TR visitReleaseValue(CAstNode N, TC C);
    protected abstract TR visitReleaseValueAddr(CAstNode N, TC C);
    protected abstract TR visitUnmanagedReleaseValue(CAstNode N, TC C);
    protected abstract TR visitDestroyValue(CAstNode N, TC C);
    protected abstract TR visitAutoreleaseValue(CAstNode N, TC C);
    protected abstract TR visitTuple(CAstNode N, TC C);
    protected abstract TR visitTupleExtract(CAstNode N, TC C);
    protected abstract TR visitTupleElementAddr(CAstNode N, TC C);
    protected abstract TR visitDestructureTuple(CAstNode N, TC C);
    protected abstract TR visitStruct(CAstNode N, TC C);
    protected abstract TR visitStructExtract(CAstNode N, TC C);
    protected abstract TR visitStructElementAddr(CAstNode N, TC C);
    protected abstract TR visitDestructureStruct(CAstNode N, TC C);
    protected abstract TR visitObject(CAstNode N, TC C);
    protected abstract TR visitRefElementAddr(CAstNode N, TC C);
    protected abstract TR visitRefTailAddr(CAstNode N, TC C);

    /* ENUMS */
    protected abstract TR visitEnum(CAstNode N, TC C);
    protected abstract TR visitUncheckedEnumData(CAstNode N, TC C);
    protected abstract TR visitInitEnumDataAddr(CAstNode N, TC C);
    protected abstract TR visitInjectEnumAddr(CAstNode N, TC C);
    protected abstract TR visitUncheckedTakeEnumDataAddr(CAstNode N, TC C);
    protected abstract TR visitSelectEnum(CAstNode N, TC C);
    protected abstract TR visitSelectEnumAddr(CAstNode N, TC C);

    /* PROTOCOL AND PROTOCOL COMPOSITION TYPES */
    protected abstract TR visitInitExistentialAddr(CAstNode N, TC C);
    protected abstract TR visitInitExistentialValue(CAstNode N, TC C);
    protected abstract TR visitDeinitExistentialAddr(CAstNode N, TC C);
    protected abstract TR visitDeinitExistentialValue(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialAddr(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialValue(CAstNode N, TC C);
    protected abstract TR visitInitExistentialRef(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialRef(CAstNode N, TC C);
    protected abstract TR visitInitExistentialMetatype(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialMetatype(CAstNode N, TC C);
    protected abstract TR visitAllocExistentialBox(CAstNode N, TC C);
    protected abstract TR visitProjectExistentialBox(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialBox(CAstNode N, TC C);
    protected abstract TR visitOpenExistentialBoxValue(CAstNode N, TC C);
    protected abstract TR visitDeallocExistentialBox(CAstNode N, TC C);

    /* BLOCKS */
    protected abstract TR visitProjectBlockStorage(CAstNode N, TC C);
    protected abstract TR visitInitBlockStorageHeader(CAstNode N, TC C);

    /* UNCHECKED CONVERSIONS */
    protected abstract TR visitUpcast(CAstNode N, TC C);
    protected abstract TR visitAddressToPointer(CAstNode N, TC C);
    protected abstract TR visitPointerToAddress(CAstNode N, TC C);
    protected abstract TR visitUncheckedRefCast(CAstNode N, TC C);
    protected abstract TR visitUncheckedRefCastAddr(CAstNode N, TC C);
    protected abstract TR visitUncheckedAddrCast(CAstNode N, TC C);
    protected abstract TR visitUncheckedTrivialBitCast(CAstNode N, TC C);
    protected abstract TR visitUncheckedBitwiseCast(CAstNode N, TC C);
    protected abstract TR visitUncheckedOwnershipConversion(CAstNode N, TC C);
    protected abstract TR visitRefToRawPointer(CAstNode N, TC C);
    protected abstract TR visitRawPointerToRef(CAstNode N, TC C);
    protected abstract TR visitRefToUnowned(CAstNode N, TC C);
    protected abstract TR visitUnownedToRef(CAstNode N, TC C);
    protected abstract TR visitRefToUnmanaged(CAstNode N, TC C);
    protected abstract TR visitUnmanagedToRef(CAstNode N, TC C);
    protected abstract TR visitConvertFunction(CAstNode N, TC C);
    protected abstract TR visitConvertEscapeToNoEscape(CAstNode N, TC C);
    protected abstract TR visitThinFunctionToPointer(CAstNode N, TC C);
    protected abstract TR visitPointerToThinFunction(CAstNode N, TC C);
    protected abstract TR visitClassifyBridgeObject(CAstNode N, TC C);
    protected abstract TR visitValueToBridgeObject(CAstNode N, TC C);
    protected abstract TR visitRefToBridgeObject(CAstNode N, TC C);
    protected abstract TR visitBridgeObjectToRef(CAstNode N, TC C);
    protected abstract TR visitBridgeObjectToWord(CAstNode N, TC C);
    protected abstract TR visitThinToThickFunction(CAstNode N, TC C);
    protected abstract TR visitThickToObjCMetatype(CAstNode N, TC C);
    protected abstract TR visitObjCToThickMetatype(CAstNode N, TC C);
    protected abstract TR visitObjCMetatypeToObject(CAstNode N, TC C);
    protected abstract TR visitObjCExistentialMetatypeToObject(CAstNode N, TC C);

    /* CHECKED CONVERSIONS */
    protected abstract TR visitUnconditionalCheckedCast(CAstNode N, TC C);
    protected abstract TR visitUnconditionalCheckedCastAddr(CAstNode N, TC C);
    protected abstract TR visitUnconditionalCheckedCastValue(CAstNode N, TC C);

    /* RUNTIME FAILURES */
    protected abstract TR visitCondFail(CAstNode N, TC C);

    /* TERMINATORS */
    protected abstract TR visitUnreachable(CAstNode N, TC C);
    protected abstract TR visitReturn(CAstNode N, TC C);
    protected abstract TR visitThrow(CAstNode N, TC C);
    protected abstract TR visitYield(CAstNode N, TC C);
    protected abstract TR visitUnwind(CAstNode N, TC C);
    protected abstract TR visitBr(CAstNode N, TC C);
    protected abstract TR visitCondBr(CAstNode N, TC C);
    protected abstract TR visitSwitchValue(CAstNode N, TC C);
    protected abstract TR visitSelectValue(CAstNode N, TC C);
    protected abstract TR visitSwitchEnum(CAstNode N, TC C);
    protected abstract TR visitSwitchEnumAddr(CAstNode N, TC C);
    protected abstract TR visitDynamicMethodBr(CAstNode N, TC C);
    protected abstract TR visitCheckedCastBr(CAstNode N, TC C);
    protected abstract TR visitCheckedCastValueBr(CAstNode N, TC C);
    protected abstract TR visitCheckedCastAddrBr(CAstNode N, TC C);
    protected abstract TR visitTryApply(CAstNode N, TC C);

}
