package ca.maple.swan.swift.translator;

import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.classLoader.ModuleEntry;

import java.net.MalformedURLException;

public class SwiftToCAstTranslatorFactory implements SwiftTranslatorFactory {
    @Override
    public TranslatorToCAst make(CAst ast, ModuleEntry M) {
        try {
            return new SwiftToCAstTranslator(M);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
