/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.codegen.source;

import java.util.List;
import org.neo4j.codegen.ByteCodes;
import org.neo4j.codegen.CodeGenerationStrategy;
import org.neo4j.codegen.CodeGenerationStrategyNotSupportedException;
import org.neo4j.codegen.CodeGeneratorOption;
import org.neo4j.codegen.CompilationFailureException;

interface JavaSourceCompiler {
    Iterable<? extends ByteCodes> compile(List<JavaSourceFile> sourceFiles, ClassLoader loader)
            throws CompilationFailureException;

    abstract class Factory implements CodeGeneratorOption {
        @Override
        public final void applyTo(Object target) {
            if (target instanceof Configuration configuration) {
                configuration.compiler = this;
                configure(configuration);
            }
        }

        abstract JavaSourceCompiler sourceCompilerFor(Configuration configuration, CodeGenerationStrategy<?> strategy)
                throws CodeGenerationStrategyNotSupportedException;

        void configure(Configuration configuration) {}
    }
}
