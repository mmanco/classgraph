/*
 * This file is part of FastClasspathScanner.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/lukehutch/fast-classpath-scanner
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.lukehutch.fastclasspathscanner.scanner;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult.InfoObject;
import io.github.lukehutch.fastclasspathscanner.typesignature.ClassRefOrTypeVariableSignature;
import io.github.lukehutch.fastclasspathscanner.typesignature.MethodTypeSignature;
import io.github.lukehutch.fastclasspathscanner.typesignature.TypeParameter;
import io.github.lukehutch.fastclasspathscanner.typesignature.TypeSignature;
import io.github.lukehutch.fastclasspathscanner.typesignature.TypeUtils;

/**
 * Holds metadata about methods of a class encountered during a scan. All values are taken directly out of the
 * classfile for the class.
 */
public class MethodInfo extends InfoObject implements Comparable<MethodInfo> {
    private final String className;
    ClassInfo classInfo;
    private final String methodName;
    private final int modifiers;
    /**
     * The JVM-internal type descriptor (missing type parameters, but including types for synthetic and mandated
     * method parameters).
     */
    private final String typeDescriptorStr;
    /** The type signature (may have type parameter information included, if present and available). */
    private final String typeSignatureStr;
    /** The parsed type signature, or if no type signature, the parsed type descriptor. */
    private MethodTypeSignature typeSignature;
    private final String[] parameterNames;
    private final int[] parameterModifiers;
    final AnnotationInfo[][] parameterAnnotationInfo;
    final List<AnnotationInfo> annotationInfo;
    private ScanResult scanResult;

    /** Sets back-reference to scan result after scan is complete. */
    @Override
    void setScanResult(final ScanResult scanResult) {
        this.scanResult = scanResult;
        if (this.annotationInfo != null) {
            for (int i = 0; i < this.annotationInfo.size(); i++) {
                final AnnotationInfo ai = this.annotationInfo.get(i);
                ai.setScanResult(scanResult);
            }
        }
        if (this.parameterAnnotationInfo != null) {
            for (int i = 0; i < this.parameterAnnotationInfo.length; i++) {
                final AnnotationInfo[] pai = this.parameterAnnotationInfo[i];
                if (pai != null) {
                    for (final AnnotationInfo ai : pai) {
                        ai.setScanResult(scanResult);
                    }
                }
            }
        }
    }

    public MethodInfo(final String className, final String methodName,
            final List<AnnotationInfo> methodAnnotationInfo, final int modifiers, final String typeDescriptorStr,
            final String typeSignatureStr, final String[] parameterNames, final int[] parameterModifiers,
            final AnnotationInfo[][] parameterAnnotationInfo) {
        this.className = className;
        this.methodName = methodName;
        this.modifiers = modifiers;
        this.typeDescriptorStr = typeDescriptorStr;
        this.typeSignatureStr = typeSignatureStr;
        this.parameterNames = parameterNames;
        this.parameterModifiers = parameterModifiers;
        this.parameterAnnotationInfo = parameterAnnotationInfo;
        this.annotationInfo = methodAnnotationInfo == null || methodAnnotationInfo.isEmpty()
                ? Collections.<AnnotationInfo> emptyList()
                : methodAnnotationInfo;
    }

    /**
     * Get the method modifiers as a string, e.g. "public static final". For the modifier bits, call
     * getAccessFlags().
     */
    public String getModifiersStr() {
        return TypeUtils.modifiersToString(getModifiers(), /* isMethod = */ true);
    }

    /**
     * Returns true if this method is a constructor. Constructors have the method name {@code
     * "<init>"}. This returns false for private static class initializer blocks, which are named
     * {@code "<clinit>"}.
     */
    public boolean isConstructor() {
        return "<init>".equals(methodName);
    }

    /** Get the name of the class this method is part of. */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the name of the method. Note that constructors are named {@code "<init>"}, and private static class
     * initializer blocks are named {@code "<clinit>"}.
     */
    public String getMethodName() {
        return methodName;
    }

    /** Returns the access flags of the method. */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * Returns the internal type descriptor for the method, e.g. {@code "(Ljava/util/List;)V"}. This is the internal
     * type descriptor used by the JVM, so does not include type parameters (due to type erasure), and does include
     * any synthetic and/or mandated parameters generated by the compiler. See also {@link getTypeDescriptor()}.
     */
    public String getTypeDescriptorStr() {
        return typeDescriptorStr;
    }

    /**
     * Returns the internal type Signature for the method, e.g. {@code "(Ljava/util/List<Ljava/lang/String;>)V"}.
     * This may or may not include synthetic and/or mandated parameters, depending on the compiler. May be null, if
     * there is no type signature in the classfile. See also {@link getTypeDescriptorStr()}.
     */
    // TODO: is the above comment about synthetic and/or mandated parameters correct?
    public String getTypeSignatureStr() {
        return typeSignatureStr;
    }

    /**
     * Returns the type signature for the method. Attempts to parse the type signature, or if not present, the type
     * descriptor.
     */
    // TODO: if both are present, compare number of parameters
    public MethodTypeSignature getTypeSignature() {
        if (typeSignature == null) {
            typeSignature = MethodTypeSignature.parse(classInfo,
                    typeSignatureStr != null ? typeSignatureStr : typeDescriptorStr);
        }
        return typeSignature;
    }

    /** Get the number of parameters in the method's type signature. */
    public int getNumParameters() {
        return getTypeSignature().getParameterTypeSignatures().size();
    }

    /**
     * Returns the result type signature for the method. If this is a constructor, the returned type will be void.
     */
    public TypeSignature getResultTypeSignature() {
        return getTypeSignature().getResultType();
    }

    /**
     * Returns the result type for the method in string representation, e.g. "char[]". If this is a constructor, the
     * returned type will be "void".
     */
    public String getResultTypeStr() {
        return getResultTypeSignature().toString();
    }

    /**
     * Returns the return type for the method as a Class reference. If this is a constructor, the return type will
     * be void.class. Note that this calls Class.forName() on the return type, which will cause the class to be
     * loaded, and possibly initialized. If the class is initialized, this can trigger side effects.
     *
     * @throws IllegalArgumentException
     *             if the return type for the method could not be loaded.
     */
    public Class<?> getResultType() throws IllegalArgumentException {
        return getResultTypeSignature().instantiate(scanResult);
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final TypeSignature[] EMPTY_TYPE_SIGNATURE_ARRAY = new TypeSignature[0];

    private static final TypeParameter[] EMPTY_TYPE_PARAMETER_ARRAY = new TypeParameter[0];

    private static final ClassRefOrTypeVariableSignature[] EMPTY_CLASS_TYPE_OR_TYPE_VARIABLE_SIGNATURE_ARRAY //
            = new ClassRefOrTypeVariableSignature[0];

    private static final Class<?>[] EMPTY_CLASS_REF_ARRAY = new Class<?>[0];

    private static String[] toStringArray(final List<?> list) {
        if (list.size() == 0) {
            return EMPTY_STRING_ARRAY;
        } else {
            final String[] stringArray = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                stringArray[i] = list.get(i).toString();
            }
            return stringArray;
        }
    }

    private static Class<?>[] toClassRefs(final List<? extends TypeSignature> typeSignatures,
            final ScanResult scanResult) {
        if (typeSignatures.size() == 0) {
            return EMPTY_CLASS_REF_ARRAY;
        } else {
            final Class<?>[] classRefArray = new Class<?>[typeSignatures.size()];
            for (int i = 0; i < typeSignatures.size(); i++) {
                classRefArray[i] = typeSignatures.get(i).instantiate(scanResult);
            }
            return classRefArray;
        }
    }

    private static TypeSignature[] toTypeSignatureArray(final List<? extends TypeSignature> typeSignatures) {
        if (typeSignatures.size() == 0) {
            return EMPTY_TYPE_SIGNATURE_ARRAY;
        } else {
            return typeSignatures.toArray(new TypeSignature[typeSignatures.size()]);
        }
    }

    private static ClassRefOrTypeVariableSignature[] toTypeOrTypeVariableSignatureArray(
            final List<? extends ClassRefOrTypeVariableSignature> typeSignatures) {
        if (typeSignatures.size() == 0) {
            return EMPTY_CLASS_TYPE_OR_TYPE_VARIABLE_SIGNATURE_ARRAY;
        } else {
            return typeSignatures.toArray(new ClassRefOrTypeVariableSignature[typeSignatures.size()]);
        }
    }

    private static TypeParameter[] toTypeParameterArray(final List<? extends TypeParameter> typeParameters) {
        if (typeParameters.size() == 0) {
            return EMPTY_TYPE_PARAMETER_ARRAY;
        } else {
            return typeParameters.toArray(new TypeParameter[typeParameters.size()]);
        }
    }

    /**
     * Returns the parameter type signatures for the method. If the method has no parameters, returns a zero-sized
     * array.
     */
    public TypeSignature[] getParameterTypeSignatures() {
        return toTypeSignatureArray(getTypeSignature().getParameterTypeSignatures());
    }

    /**
     * Returns the parameter types for the method. If the method has no parameters, returns a zero-sized array.
     *
     * <p>
     * Note that this calls Class.forName() on the parameter types, which will cause the class to be loaded, and
     * possibly initialized. If the class is initialized, this can trigger side effects.
     *
     * @throws IllegalArgumentException
     *             if the parameter types of the method could not be loaded.
     */
    public Class<?>[] getParameterTypes() throws IllegalArgumentException {
        return toClassRefs(getTypeSignature().getParameterTypeSignatures(), scanResult);
    }

    /**
     * Returns the parameter types for the method in string representation, e.g. {@code ["int",
     * "List<X>", "com.abc.XYZ"]}. If the method has no parameters, returns a zero-sized array.
     */
    public String[] getParameterTypeStrs() {
        return toStringArray(getTypeSignature().getParameterTypeSignatures());
    }

    /**
     * Returns the types of exceptions the method may throw, in string representation, e.g. {@code
     * ["com.abc.BadException", "<X>"]}. If the method throws no exceptions, returns a zero-sized array.
     */
    public ClassRefOrTypeVariableSignature[] getThrowsTypeSignatures() {
        return toTypeOrTypeVariableSignatureArray(getTypeSignature().getThrowsSignatures());
    }

    /**
     * Returns the types of exceptions the method may throw. If the method throws no exceptions, returns a
     * zero-sized array.
     */
    public Class<?>[] getThrowsTypes() {
        return toClassRefs(getTypeSignature().getThrowsSignatures(), scanResult);
    }

    /**
     * Returns the types of exceptions the method may throw, in string representation, e.g. {@code
     * ["com.abc.BadException", "<X>"]}. If the method throws no exceptions, returns a zero-sized array.
     */
    public String[] getThrowsTypeStrs() {
        return toStringArray(getTypeSignature().getThrowsSignatures());
    }

    /**
     * Returns the type parameters of the method. If the method has no type parameters, returns a zero-sized array.
     */
    public TypeParameter[] getTypeParameters() {
        return toTypeParameterArray(getTypeSignature().getTypeParameters());
    }

    /**
     * Returns the type parameters of the method, in string representation, e.g. {@code ["<X>",
     * "<Y>"]}. If the method has no type parameters, returns a zero-sized array.
     */
    public String[] getTypeParameterStrs() {
        return toStringArray(getTypeSignature().getTypeParameters());
    }

    /** Returns true if this method is public. */
    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    /** Returns true if this method is private. */
    public boolean isPrivate() {
        return Modifier.isPrivate(modifiers);
    }

    /** Returns true if this method is protected. */
    public boolean isProtected() {
        return Modifier.isProtected(modifiers);
    }

    /** Returns true if this method is package-private. */
    public boolean isPackagePrivate() {
        return !isPublic() && !isPrivate() && !isProtected();
    }

    /** Returns true if this method is static. */
    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    /** Returns true if this method is final. */
    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    /** Returns true if this method is synchronized. */
    public boolean isSynchronized() {
        return Modifier.isSynchronized(modifiers);
    }

    /** Returns true if this method is a bridge method. */
    public boolean isBridge() {
        // From: http://anonsvn.jboss.org/repos/javassist/trunk/src/main/javassist/bytecode/AccessFlag.java
        return (modifiers & 0x0040) != 0;
    }

    /** Returns true if this method is a varargs method. */
    public boolean isVarArgs() {
        // From: http://anonsvn.jboss.org/repos/javassist/trunk/src/main/javassist/bytecode/AccessFlag.java
        return (modifiers & 0x0080) != 0;
    }

    /** Returns true if this method is a native method. */
    public boolean isNative() {
        return Modifier.isNative(modifiers);
    }

    /**
     * Returns the method parameter names, if available (only available in classfiles compiled in JDK8 or above
     * using the -parameters commandline switch), otherwise returns null.
     *
     * <p>
     * Note that parameters may be unnamed, in which case the corresponding parameter name will be null.
     */
    public String[] getParameterNames() {
        if (parameterNames == null) {
            return null;
        }
        if (getNumParameters() != parameterNames.length) {
            // Kotlin stores the wrong number of parameter names in some circumstances --
            // if this happens, just ignore the parameter names (there is no way to align them)
            return null;
        }
        return parameterNames;
    }

    /**
     * Returns the parameter modifiers, if available (only available in classfiles compiled in JDK8 or above using
     * the -parameters commandline switch, or code compiled with Kotlin or some other language), otherwise returns
     * null.
     * 
     * <p>
     * Flag bits:
     *
     * <ul>
     * <li>0x0010 (ACC_FINAL): Indicates that the formal parameter was declared final.
     * <li>0x1000 (ACC_SYNTHETIC): Indicates that the formal parameter was not explicitly or implicitly declared in
     * source code, according to the specification of the language in which the source code was written (JLS §13.1).
     * (The formal parameter is an implementation artifact of the compiler which produced this class file.)
     * <li>0x8000 (ACC_MANDATED): Indicates that the formal parameter was implicitly declared in source code,
     * according to the specification of the language in which the source code was written (JLS §13.1). (The formal
     * parameter is mandated by a language specification, so all compilers for the language must emit it.)
     * </ul>
     */
    public int[] getParameterModifiers() {
        if (parameterModifiers == null) {
            return null;
        }
        if (getNumParameters() != parameterModifiers.length) {
            // Kotlin stores the wrong number of parameter modifiers in some circumstances --
            // if this happens, just ignore the parameter modifiers (there is no way to align them)
            return null;
        }
        return parameterModifiers;
    }

    /**
     * Returns the parameter modifiers as a string (e.g. ["final", ""], if available (only available in classfiles
     * compiled in JDK8 or above using the -parameters commandline switch), otherwise returns null.
     */
    public String[] getParameterModifierStrs() {
        final int[] paramModifiers = getParameterModifiers();
        if (paramModifiers == null) {
            return null;
        }
        final String[] paramModifierStrs = new String[paramModifiers.length];
        for (int i = 0; i < paramModifiers.length; i++) {
            paramModifierStrs[i] = TypeUtils.modifiersToString(paramModifiers[i], /* isMethod = */ false);
        }
        return paramModifierStrs;
    }

    /**
     * Returns the annotations on each method parameter (along with any annotation parameters, wrapped in
     * AnnotationInfo objects) if any parameters have annotations, else returns null.
     */
    public AnnotationInfo[][] getParameterAnnotationInfo() {
        if (parameterAnnotationInfo == null) {
            return null;
        }
        if (getNumParameters() != parameterAnnotationInfo.length) {
            // Kotlin may store the wrong number of annotations in some circumstances --
            // if this happens, just ignore the parameter annotations (there is no way to align them)
            return null;
        }
        return parameterAnnotationInfo;
    }

    /**
     * Returns the unique annotation names for annotations on each method parameter, if any parameters have
     * annotations, else returns null.
     */
    public String[][] getParameterAnnotationNames() {
        final AnnotationInfo[][] paramAnnotationInfo = getParameterAnnotationInfo();
        if (paramAnnotationInfo == null) {
            return null;
        }
        final String[][] paramAnnotationNames = new String[paramAnnotationInfo.length][];
        for (int i = 0; i < paramAnnotationInfo.length; i++) {
            paramAnnotationNames[i] = AnnotationInfo.getUniqueAnnotationNamesSorted(paramAnnotationInfo[i]);
        }
        return paramAnnotationNames;
    }

    /**
     * Returns the unique annotation types for annotations on each method parameter, if any parameters have
     * annotations, else returns null.
     */
    public Class<?>[][] getParameterAnnotationTypes() {
        final String[][] paramAnnotationNames = getParameterAnnotationNames();
        if (paramAnnotationNames == null) {
            return null;
        }
        final Class<?>[][] parameterAnnotationTypes = new Class<?>[paramAnnotationNames.length][];
        for (int i = 0; i < parameterAnnotationInfo.length; i++) {
            parameterAnnotationTypes[i] = new Class<?>[paramAnnotationNames[i].length];
            for (int j = 0; j < paramAnnotationNames[i].length; j++) {
                parameterAnnotationTypes[i][j] = scanResult.classNameToClassRef(paramAnnotationNames[i][j]);
            }
        }
        return parameterAnnotationTypes;
    }

    /** Returns the names of annotations on the method, or the empty list if none. */
    public List<String> getAnnotationNames() {
        return Arrays.asList(AnnotationInfo.getUniqueAnnotationNamesSorted(annotationInfo));
    }

    /**
     * Returns a list of Class<?> references for the annotations on this method, or the empty list if none. Note
     * that this calls Class.forName() on the annotation types, which will cause each annotation class to be loaded.
     *
     * @throws IllegalArgumentException
     *             if the annotation type could not be loaded.
     */
    public List<Class<?>> getAnnotationTypes() throws IllegalArgumentException {
        if (annotationInfo == null || annotationInfo.isEmpty()) {
            return Collections.<Class<?>> emptyList();
        } else {
            final List<Class<?>> annotationClassRefs = new ArrayList<>();
            for (final String annotationName : getAnnotationNames()) {
                annotationClassRefs.add(scanResult.classNameToClassRef(annotationName));
            }
            return annotationClassRefs;
        }
    }

    /**
     * Get a list of annotations on this method, along with any annotation parameter values, wrapped in
     * AnnotationInfo objects, or the empty list if none.
     */
    public List<AnnotationInfo> getAnnotationInfo() {
        return annotationInfo == null ? Collections.<AnnotationInfo> emptyList() : annotationInfo;
    }

    /** Test class name, method name and type descriptor for equals(). */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        final MethodInfo other = (MethodInfo) obj;
        return className.equals(other.className) && typeDescriptorStr.equals(other.typeDescriptorStr)
                && methodName.equals(other.methodName);
    }

    /** Use hash code of class name, method name and type descriptor. */
    @Override
    public int hashCode() {
        return methodName.hashCode() + typeDescriptorStr.hashCode() * 11 + className.hashCode() * 57;
    }

    /** Sort in order of class name, method name, then type descriptor. */
    @Override
    public int compareTo(final MethodInfo other) {
        final int diff0 = className.compareTo(other.className);
        if (diff0 != 0) {
            return diff0;
        }
        final int diff1 = methodName.compareTo(other.methodName);
        if (diff1 != 0) {
            return diff1;
        }
        return typeDescriptorStr.compareTo(other.typeDescriptorStr);
    }

    /**
     * Get a string representation of the method. Note that constructors are named {@code "<init>"}, and private
     * static class initializer blocks are named {@code "<clinit>"}.
     */
    @Override
    public String toString() {
        return getTypeSignature().toString(getAnnotationInfo(), getModifiers(), isConstructor(), methodName,
                isVarArgs(), getParameterNames(), getParameterModifiers(), getParameterAnnotationInfo());
    }
}
