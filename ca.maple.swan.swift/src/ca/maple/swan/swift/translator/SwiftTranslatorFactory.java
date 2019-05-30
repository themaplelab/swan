package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.classLoader.ModuleEntry;

public interface SwiftTranslatorFactory {
    TranslatorToCAst make(CAst ast, ModuleEntry M);
}
