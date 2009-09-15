/*
 * Copyright 2004-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.slim3.gen.processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import org.slim3.gen.ClassConstants;
import org.slim3.gen.desc.ModelMetaDesc;
import org.slim3.gen.desc.MustangAttributeMetaDescFactory;
import org.slim3.gen.desc.MustangModelMetaDescFactory;
import org.slim3.gen.generator.Generator;
import org.slim3.gen.generator.ModelMetaGenerator;
import org.slim3.gen.message.MessageCode;
import org.slim3.gen.message.MessageFormatter;

/**
 * Processes JDO model classes which annotated with the {@code
 * javax.jdo.annotations.PersistenceCapable} class.
 * 
 * @author taedium
 * @since 3.0
 * 
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes(ClassConstants.PersistenceCapable)
@SupportedOptions( {
    MustangOptions.DEBUG,
    MustangOptions.JAVA_VERSION,
    MustangOptions.MODEL_PACKAGE,
    MustangOptions.META_PACKAGE,
    MustangOptions.SHARED_PACKAGE,
    MustangOptions.SERVER_PACKAGE })
public class MustangModelProcessor extends AbstractProcessor {

    /** the support for generating */
    protected MustangGenerateSupport generateSupport;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        generateSupport = new MustangGenerateSupport(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (MustangOptions.getJavaVersion(processingEnv) < 1.6) {
            MustangLogger.debug(processingEnv, MessageFormatter.getMessage(
                MessageCode.SILM3GEN0012,
                getClass().getName()));
            return true;
        }
        if (roundEnv.processingOver()) {
            return true;
        }
        for (TypeElement annotation : annotations) {
            for (TypeElement element : ElementFilter.typesIn(roundEnv
                .getElementsAnnotatedWith(annotation))) {
                try {
                    handleTypeElement(element);
                } catch (RuntimeException e) {
                    MustangLogger.error(
                        processingEnv,
                        element,
                        MessageFormatter.getMessage(
                            MessageCode.SILM3GEN0001,
                            ClassConstants.PersistenceCapable));
                    throw e;
                }
            }
        }
        return true;
    }

    /**
     * Handles a type element represents a JDO model class.
     * 
     * @param element
     *            the element represents a JDO model class.
     */
    protected void handleTypeElement(TypeElement element) {
        if (MustangOptions.isDebugEnabled(processingEnv)) {
            MustangLogger.debug(processingEnv, MessageFormatter.getMessage(
                MessageCode.SILM3GEN0002,
                element));
        }
        if (element.getNestingKind() == NestingKind.TOP_LEVEL) {
            MustangAttributeMetaDescFactory attributeMetaDescFactory =
                createAttributeMetaDescFactory();
            MustangModelMetaDescFactory modelMetaDescFactory =
                createModelMetaDescFactory(attributeMetaDescFactory);
            ModelMetaDesc modelMetaDesc =
                modelMetaDescFactory.createModelMetaDesc(element);
            Generator generator = createGenerator(modelMetaDesc);
            generateSupport.generate(generator, modelMetaDesc, element);
        }
        if (MustangOptions.isDebugEnabled(processingEnv)) {
            MustangLogger.debug(processingEnv, MessageFormatter.getMessage(
                MessageCode.SILM3GEN0003,
                element));
        }
    }

    /**
     * Creates an attribute meta description factory.
     * 
     * @return an attribute meta description factory
     */
    protected MustangAttributeMetaDescFactory createAttributeMetaDescFactory() {
        return new MustangAttributeMetaDescFactory(processingEnv);
    }

    /**
     * Creates a model meta description factory.
     * 
     * @param attributeMetaDescFactory
     *            the attribute meta description factory.
     * @return a model meta description factory
     */
    protected MustangModelMetaDescFactory createModelMetaDescFactory(
            MustangAttributeMetaDescFactory attributeMetaDescFactory) {
        return new MustangModelMetaDescFactory(
            processingEnv,
            attributeMetaDescFactory);
    }

    /**
     * Creates a generator object.
     * 
     * @param modelMetaDesc
     *            the model description.
     * @return a generator object.
     */
    protected Generator createGenerator(ModelMetaDesc modelMetaDesc) {
        return new ModelMetaGenerator(modelMetaDesc);
    }
}