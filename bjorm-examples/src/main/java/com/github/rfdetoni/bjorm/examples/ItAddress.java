package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Column;

/** Immutable value object; never a standalone @Table. */
public record ItAddress(String street,@Column("postal") String zipCode) {}
