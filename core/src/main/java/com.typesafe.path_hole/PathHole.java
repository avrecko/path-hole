/*
 * Copyright 2012 Typesafe Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.typesafe.path_hole;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;

/**
 * Throws {@link ClassNotFoundException} for classes on the classpath but matching the path-hole agent filter.
 * <p/>
 * <p>Usage java -javaagent:boot-hole.jar -Dboot_hole.filter=com.foo.Bar,com.baz.*Test</p>
 *
 * @author Alen Vre\u010Dko
 */
public class PathHole {

    public static final String FILTER_PROPERTY_NAME = "boot_hole.filter";


    public static void premain(String agentArguments, Instrumentation instrumentation) {
        // check if we can redefine the java.lang.ClassLoader
        checkArgument(instrumentation.isRedefineClassesSupported(), "Class redefinition not supported on this JVM. Remove the boot-hole agent.");

        // lets prepend the loadClass method with our filter
        byte[] enhancedBytes = prependToClassLoader();

        // now we can redefine it
        try {
            instrumentation.redefineClasses(new ClassDefinition(ClassLoader.class, enhancedBytes));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

    }


    // we will get this method's bytecode and prepend it to the bytecode of java.lang.ClassLoader#loadClass(String,Bool)
    public void bytecodeToPrepend(String name, boolean resolve) throws ClassNotFoundException {
        // might not seem optimal to read properties each time but this allows for riches runtime behavior
        String filter = System.getProperty(PathHole.FILTER_PROPERTY_NAME, "").trim();

        if (!filter.isEmpty() && !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("com.sun.") && !name.startsWith("sun.")) {
            List<Pattern> patterns = new ArrayList<Pattern>();

            // we will parse the entries each time as we cannot cache this easily
            // a Cache field cannot be added due to the limitations of the redefine mechanism
            // we cannot reference non bootclasspath entries (rt.jar) as this is one level lower as the application CL

            // premature optimization is the root of all evil. As long as this will work fast enough will leave as is.
            Pattern FQN_PATTERN = Pattern.compile("([\\p{L}_$\\*][\\p{L}\\p{N}_$\\*]*\\.)*[\\p{L}_$\\*][\\p{L}\\p{N}_$\\*]*");

            String[] split = filter.split(",");
            for (String s : split) {
                if (s != null && s.trim().length() > 0) {
                    if (FQN_PATTERN.matcher(s.trim()).matches()) {
                        patterns.add(Pattern.compile(s.trim().replace(".", "\\.").replace("*", ".+")));
                    } else {
                        Logger.getGlobal().log(Level.WARNING, "Path Hole Agent has malformed filter entry = " + s);
                    }
                }
            }

            for (Pattern pattern : patterns) {
                if (pattern.matcher(name).matches()) {
                    throw new ClassNotFoundException(name.replace(".", "/"));
                }

            }


        }
    }

    private static byte[] prependToClassLoader() {
        Object[] ourEnhancementsToLoadClass = getMethodNode(PathHole.class, "bytecodeToPrepend", "(Ljava/lang/String;Z)V");
        Object[] jlClassLoader = getMethodNode(ClassLoader.class, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");

        // get the bytecode to prepend
        InsnList prependInst = ((MethodNode) ourEnhancementsToLoadClass[1]).instructions;

        // lets get rid of the return statement
        // remove the optional label
        if (prependInst.getLast().getOpcode() < 0) {
            prependInst.remove(prependInst.getLast());
        }
        // remove the return inst. It doesn't take any args so this is all we need
        prependInst.remove(prependInst.getLast());

        // now add this to loadClass method of jlClassLoader
        InsnList baseInst = ((MethodNode) jlClassLoader[1]).instructions;
        baseInst.insertBefore(baseInst.getFirst(), prependInst);

        // we just need to add any fields referenced by the prepended bytecode to the jlClassLoader
        ClassNode clClassNode = (ClassNode) jlClassLoader[0];
        ClassNode prependClassNode = (ClassNode) ourEnhancementsToLoadClass[0];

        // write the new bytecode
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        clClassNode.accept(cw);
        return cw.toByteArray();
    }

    public static Object[] getMethodNode(Class<?> clazz, String methodName, String methodDescription) {

        ClassReader cr = new ClassReader(getByteCode(clazz));
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        for (Object mnRaw : classNode.methods) {
            MethodNode mn = (MethodNode) mnRaw;
            if (mn.name.equals(methodName) && mn.desc.equals(methodDescription)) {
                return new Object[]{classNode, mn};
            }

        }
        throw new IllegalArgumentException("No method found for the provided params [clazz=" + clazz.getName() + ", methodName=" + methodName + ", desc=" + methodDescription);
    }

    public static byte[] getByteCode(Class<?> clazz) {
        try {
            return toByteArray(getResource(clazz.getName().replace(".", "/") + ".class"));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
