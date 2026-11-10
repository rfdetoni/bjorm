package com.github.rfdetoni.bjorm.examples;

import com.github.rfdetoni.bjorm.Query;

/** Compile-time native SQL dialect check must ignore syntax inside quoted literals/comments. */
public interface SqlCastQueries {
    @Query("SELECT ':uuid::type' /* :: only in a comment */")
    String safeLiteral();
}
