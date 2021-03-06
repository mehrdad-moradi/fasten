/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.enums;


import eu.fasten.core.data.metadatadb.codegen.Public;

import javax.annotation.processing.Generated;

import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.12.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public enum Access implements EnumType {

    private_("private"),

    public_("public"),

    packagePrivate("packagePrivate"),

    static_("static"),

    protected_("protected");

    private final String literal;

    private Access(String literal) {
        this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
        return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public String getName() {
        return "access";
    }

    @Override
    public String getLiteral() {
        return literal;
    }
}
