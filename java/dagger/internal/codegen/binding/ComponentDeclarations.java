/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.binding.SourceFiles.generatedMonitoringModuleName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Key.MultibindingContributionIdentifier;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Optional;
import javax.inject.Inject;

/** Stores the bindings and declarations of a component by key. */
final class ComponentDeclarations {
  private static ImmutableSet<TypeName> FRAMEWORK_TYPENAMES =
      ImmutableSet.of(TypeNames.PROVIDER, TypeNames.PRODUCER, TypeNames.PRODUCED);

  private final KeyFactory keyFactory;
  private final ImmutableSetMultimap<Key, ContributionBinding> bindings;
  private final ImmutableSetMultimap<Key, DelegateDeclaration> delegates;
  private final ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindings;
  private final ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponents;
  private final ImmutableSetMultimap<TypeNameKey, MultibindingDeclaration> multibindings;
  private final ImmutableSetMultimap<TypeNameKey, ContributionBinding> multibindingContributions;
  private final ImmutableSetMultimap<TypeNameKey, DelegateDeclaration>
      delegateMultibindingContributions;

  private ComponentDeclarations(
      KeyFactory keyFactory,
      ImmutableSetMultimap<Key, ContributionBinding> bindings,
      ImmutableSetMultimap<Key, DelegateDeclaration> delegates,
      ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindings,
      ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponents,
      ImmutableSetMultimap<TypeNameKey, MultibindingDeclaration> multibindings,
      ImmutableSetMultimap<TypeNameKey, ContributionBinding> multibindingContributions,
      ImmutableSetMultimap<TypeNameKey, DelegateDeclaration> delegateMultibindingContributions) {
    this.keyFactory = keyFactory;
    this.bindings = bindings;
    this.delegates = delegates;
    this.optionalBindings = optionalBindings;
    this.subcomponents = subcomponents;
    this.multibindings = multibindings;
    this.multibindingContributions = multibindingContributions;
    this.delegateMultibindingContributions = delegateMultibindingContributions;
  }

  ImmutableSet<ContributionBinding> bindings(Key key) {
    return bindings.get(key);
  }

  ImmutableSet<DelegateDeclaration> delegates(Key key) {
    return delegates.get(key);
  }

  /**
   * Returns the delegate multibinding contributions (e.g. {@code @Binds @IntoMap}) for the given
   * {@code multibindingKey}, or an empty set if it's not a valid multibinding key.
   *
   * <p>A valid {@code multibindingKey} has one of the following types:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   *
   * <p>Note that {@code Map<K, V>} and {@code Map<K, Provider<V>>}, e.g., represent the same
   * underlying multibinding and will return the same contributions.
   */
  ImmutableSet<DelegateDeclaration> delegateMultibindingContributions(Key multibindingKey) {
    return delegateMultibindingContributions.get(
        unwrapMultibindingKey(multibindingKey, keyFactory));
  }

  /**
   * Returns the multibinding declarations (i.e. {@code @Multibinds}) for the given
   * {@code multibindingKey}, or an empty set if it's not a valid multibinding key.
   *
   * <p>A valid {@code multibindingKey} has one of the following types:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   *
   * <p>Note that {@code Map<K, V>} and {@code Map<K, Provider<V>>}, e.g., represent the same
   * underlying multibinding and will return the same declarations.
   */
  ImmutableSet<MultibindingDeclaration> multibindings(Key multibindingKey) {
    return multibindings.get(unwrapMultibindingKey(multibindingKey, keyFactory));
  }

  /**
   * Returns the multibinding contributions (e.g. {@code @Provides @IntoMap}) for the given
   * {@code multibindingKey}, or an empty set if it's not a valid multibinding key.
   *
   * <p>A valid {@code multibindingKey} has one of the following types:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   *
   * <p>Note that {@code Map<K, V>} and {@code Map<K, Provider<V>>}, e.g., represent the same
   * underlying multibinding and will return the same contributions.
   */
  ImmutableSet<ContributionBinding> multibindingContributions(Key multibindingKey) {
    return multibindingContributions.get(unwrapMultibindingKey(multibindingKey, keyFactory));
  }

  ImmutableSet<OptionalBindingDeclaration> optionalBindings(Key key) {
    return optionalBindings.get(key);
  }

  ImmutableSet<SubcomponentDeclaration> subcomponents(Key key) {
    return subcomponents.get(key);
  }

  ImmutableSet<BindingDeclaration> allDeclarations() {
    return ImmutableSet.<BindingDeclaration>builder()
        .addAll(bindings.values())
        .addAll(delegates.values())
        .addAll(multibindings.values())
        .addAll(optionalBindings.values())
        .addAll(subcomponents.values())
        .build();
  }

  static final class Factory {
    private final XProcessingEnv processingEnv;
    private final KeyFactory keyFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        KeyFactory keyFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory) {
      this.processingEnv = processingEnv;
      this.keyFactory = keyFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
    }

    ComponentDeclarations create(
        Optional<ComponentDescriptor> parentDescriptor, ComponentDescriptor descriptor) {
      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegates = ImmutableSet.builder();
      ImmutableSet.Builder<MultibindingDeclaration> multibindings = ImmutableSet.builder();
      ImmutableSet.Builder<OptionalBindingDeclaration> optionalBindings =ImmutableSet.builder();
      ImmutableSet.Builder<SubcomponentDeclaration> subcomponents = ImmutableSet.builder();

      bindings.addAll(descriptor.bindings());
      delegates.addAll(descriptor.delegateDeclarations());
      multibindings.addAll(descriptor.multibindingDeclarations());
      optionalBindings.addAll(descriptor.optionalBindingDeclarations());
      subcomponents.addAll(descriptor.subcomponentDeclarations());

      // Note: The implicit production modules are not included directly in the component descriptor
      // because we don't know whether to install them or not without knowing the parent component.
      for (ModuleDescriptor module : implicitProductionModules(descriptor, parentDescriptor)) {
        bindings.addAll(module.bindings());
        delegates.addAll(module.delegateDeclarations());
        multibindings.addAll(module.multibindingDeclarations());
        optionalBindings.addAll(module.optionalDeclarations());
        subcomponents.addAll(module.subcomponentDeclarations());
      }

      return new ComponentDeclarations(
          keyFactory,
          indexDeclarationsByKey(bindings.build()),
          indexDeclarationsByKey(delegates.build()),
          indexDeclarationsByKey(optionalBindings.build()),
          indexDeclarationsByKey(subcomponents.build()),
          // The @Multibinds declarations and @IntoSet/@IntoMap multibinding contributions are all
          // indexed by their "unwrapped" multibinding key (i.e. Map<K, V> or Set<V>) so that we
          // don't have to check multiple different keys to gather all of the contributions.
          indexDeclarationsByUnwrappedMultibindingKey(multibindings.build()),
          indexDeclarationsByUnwrappedMultibindingKey(multibindingContributions(bindings.build())),
          indexDeclarationsByUnwrappedMultibindingKey(
              multibindingContributions(delegates.build())));
    }

    /**
     * Returns all the modules that should be installed in the component. For production components
     * and production subcomponents that have a parent that is not a production component or
     * subcomponent, also includes the production monitoring module for the component and the
     * production executor module.
     */
    private ImmutableSet<ModuleDescriptor> implicitProductionModules(
        ComponentDescriptor descriptor, Optional<ComponentDescriptor> parentDescriptor) {
      return shouldIncludeImplicitProductionModules(descriptor, parentDescriptor)
          ? ImmutableSet.of(
              moduleDescriptorFactory.create(
                  DaggerSuperficialValidation.requireTypeElement(
                      processingEnv, generatedMonitoringModuleName(descriptor.typeElement()))),
              moduleDescriptorFactory.create(
                  processingEnv.requireTypeElement(TypeNames.PRODUCTION_EXECTUTOR_MODULE)))
          : ImmutableSet.of();
    }

    private static boolean shouldIncludeImplicitProductionModules(
        ComponentDescriptor descriptor, Optional<ComponentDescriptor> parentDescriptor) {
      return descriptor.isProduction()
          && descriptor.isRealComponent()
          && (parentDescriptor.isEmpty() || !parentDescriptor.get().isProduction());
    }

    /** Indexes {@code bindingDeclarations} by {@link BindingDeclaration#key()}. */
    private static <T extends BindingDeclaration>
        ImmutableSetMultimap<Key, T> indexDeclarationsByKey(Iterable<T> declarations) {
      return ImmutableSetMultimap.copyOf(Multimaps.index(declarations, BindingDeclaration::key));
    }

    /** Indexes {@code bindingDeclarations} by the unwrapped multibinding key. */
    private <T extends BindingDeclaration> ImmutableSetMultimap<TypeNameKey, T>
        indexDeclarationsByUnwrappedMultibindingKey(Iterable<T> declarations) {
      return ImmutableSetMultimap.copyOf(
          Multimaps.index(
              declarations,
              declaration ->
                  unwrapMultibindingKey(
                      declaration.key().withoutMultibindingContributionIdentifier(), keyFactory)));
    }

    private static <T extends BindingDeclaration> ImmutableSet<T> multibindingContributions(
        ImmutableSet<T> declarations) {
      return declarations.stream()
          .filter(declaration -> declaration.key().multibindingContributionIdentifier().isPresent())
          .collect(toImmutableSet());
    }
  }

  private static TypeNameKey unwrapMultibindingKey(Key key, KeyFactory keyFactory) {
    if (MapType.isMap(key)) {
      return TypeNameKey.from(
          key.multibindingContributionIdentifier(),
          key.qualifier(),
          keyFactory.unwrapMapValueType(key).type().xprocessing().getTypeName());
    }
    if (SetType.isSet(key)) {
      return TypeNameKey.from(
          key.multibindingContributionIdentifier(),
          key.qualifier(),
          keyFactory.unwrapSetKey(key, TypeNames.PRODUCED).type().xprocessing().getTypeName());
    }
    return TypeNameKey.from(
          key.multibindingContributionIdentifier(),
          key.qualifier(),
          key.type().xprocessing().getTypeName());
  }

  private static TypeName unwrapMultibindingMapValue(ParameterizedTypeName mapTypeName) {
    if (mapTypeName.typeArguments.get(1) instanceof ParameterizedTypeName) {
      TypeName keyTypeName = mapTypeName.typeArguments.get(0);
      ParameterizedTypeName valueTypeName =
          (ParameterizedTypeName) mapTypeName.typeArguments.get(1);
      if (FRAMEWORK_TYPENAMES.contains(valueTypeName.rawType)) {
        return ParameterizedTypeName.get(
            TypeNames.MAP, keyTypeName, valueTypeName.typeArguments.get(0));
      }
    }
    return mapTypeName;
  }

  private static TypeName unwrapMultibindingSetValue(ParameterizedTypeName setTypeName) {
    if (setTypeName.typeArguments.get(0) instanceof ParameterizedTypeName) {
      ParameterizedTypeName valueTypeName =
          (ParameterizedTypeName) setTypeName.typeArguments.get(0);
      if (valueTypeName.rawType.equals(TypeNames.PRODUCED)) {
        return ParameterizedTypeName.get(TypeNames.SET, valueTypeName.typeArguments.get(0));
      }
    }
    return setTypeName;
  }

  /**
   * Returns {@code true} if the given {@code key}'s type has a valid multibinding type.
   *
   * <p>Note that this doesn't mean that the key matches a multibinding in the BindingGraph, just
   * that its type could match a multibinding. In particular, we check that the Set/Map is not a
   * raw type, and that its type arguments are not wildcards (all synthetic multibinding key types
   * have invariant type arguments).
   */
  private static boolean isValidMultibindingKey(Key key) {
    if (XTypes.isRawParameterizedType(key.type().xprocessing())) {
      return false;
    }
    // Note: By starting with the TypeName of the entire Map/Set, we ensure that any variance in
    // the original TypeName is consistent as we traverse through the type arguments. If we
    // traverse through the type arguments directly using XType and then get the TypeName, we
    // can get the wrong type variance (see b/352142595).
    if (MapType.isMap(key)) {
      ParameterizedTypeName mapTypeName =
          (ParameterizedTypeName) key.type().xprocessing().getTypeName();
      return !(mapTypeName.typeArguments.get(0) instanceof WildcardTypeName)
          && !(mapTypeName.typeArguments.get(1) instanceof WildcardTypeName);
    } else if (SetType.isSet(key)) {
      ParameterizedTypeName setTypeName =
          (ParameterizedTypeName) key.type().xprocessing().getTypeName();
      return !(setTypeName.typeArguments.get(0) instanceof WildcardTypeName);
    } else {
      return false;
    }
  }

  /** Similar to {@link Key} but uses a {@link TypeName} rather than an {@code XType}. */
  // Note: We use TypeNameKey for multibindings because multibindings are grouped by a derived type
  // that may be different than the exact XType provided by the user. Due to b/352142595, we lose
  // variance information when creating derived XTypes so we deal with TypeName to avoid that issue.
  @AutoValue
  abstract static class TypeNameKey {
    static TypeNameKey from(
        Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier,
        Optional<DaggerAnnotation> qualifier,
        TypeName typeName) {
      return new AutoValue_ComponentDeclarations_TypeNameKey(
          multibindingContributionIdentifier, qualifier, typeName);
    }

    abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();

    abstract Optional<DaggerAnnotation> qualifier();

    abstract TypeName type();
  }
}
