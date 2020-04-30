/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.utils;

// This class MUST be used whenever handling exceptions as later on when we
// need to report errors to the frontend, we will know about them
// even if they are deep and caught.
public class ExceptionReporter {

    // Add as needed

    public static <T extends Exception> void report(T e) throws T {
        // Do something with the exception here.

        throw e;
    }
}
