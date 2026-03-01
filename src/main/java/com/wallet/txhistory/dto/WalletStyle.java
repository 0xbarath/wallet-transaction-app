package com.wallet.txhistory.dto;

import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Value.Style(
        visibility = ImplementationVisibility.PUBLIC,
        builderVisibility = BuilderVisibility.PUBLIC,
        defaults = @Value.Immutable(copy = true),
        jdkOnly = true,
        get = {"get*", "is*", "*"},
        jacksonIntegration = true
)
public @interface WalletStyle {}
