package com.uwaterloo.datadriven.model.framework.field;

public enum AccessType {
    // When the field is directly returned by an API
    GET,
    // When the field is directly set through parameter(s) of an API
    SET,
    // When the field is accessed, but not directly returned, by an API
    FETCH,
    // When the field is modified by an API, but cannot be directly influenced through parameters
    MODIFY,
    // When the existence of an index is checked

    /*
    The next four operations are specific to collection fields
     */
    // When the existence of an index is checked
    INDEX_EXISTS,
    // When the existence of a value is checked
    VALUE_EXISTS,
    // When a new field is added to a collection
    ADD,
    // When the field is removed from a collection
    REMOVE,

    // All(or any) access to the field
    ;

    public boolean isPrecise() {
        return !this.equals(FETCH) && !this.equals(MODIFY);
    }
}
