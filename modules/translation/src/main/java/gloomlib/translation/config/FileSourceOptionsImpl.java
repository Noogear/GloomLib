package gloomlib.translation.config;

import gloomlib.translation.api.TranslationMigrator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link FileSourceOptions}.
 */
record FileSourceOptionsImpl(
        boolean createIfMissing,
        boolean enableMigration,
        boolean backupBeforeMigration,
        boolean verbose,
        @NotNull String internalPathPrefix,
        @NotNull Charset charset,
        @Nullable String langVersion,
        @NotNull FileSourceOptions.Scope scope,
        @Nullable TranslationMigrator migrator
) implements FileSourceOptions {

    static final class BuilderImpl implements FileSourceOptions.Builder {
        private boolean createIfMissing = true;
        private boolean enableMigration = true;
        private boolean backupBeforeMigration = false;
        private boolean verbose = false;
        private String internalPathPrefix = "translations/";
        private Charset charset = StandardCharsets.UTF_8;
        private String langVersion = null;
        private FileSourceOptions.Scope scope = FileSourceOptions.Scope.FILL;
        private TranslationMigrator migrator = null;

        @Override
        public @NotNull FileSourceOptions.Builder createIfMissing(boolean createIfMissing) {
            this.createIfMissing = createIfMissing;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder enableMigration(boolean enableMigration) {
            this.enableMigration = enableMigration;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder backupBeforeMigration(boolean backupBeforeMigration) {
            this.backupBeforeMigration = backupBeforeMigration;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder internalPathPrefix(@NotNull String prefix) {
            this.internalPathPrefix = prefix;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder charset(@NotNull Charset charset) {
            this.charset = charset;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder langVersion(@Nullable String version) {
            this.langVersion = version;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder scope(@NotNull FileSourceOptions.Scope scope) {
            this.scope = scope;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions.Builder migrator(@Nullable TranslationMigrator migrator) {
            this.migrator = migrator;
            return this;
        }

        @Override
        public @NotNull FileSourceOptions build() {
            return new FileSourceOptionsImpl(
                    createIfMissing,
                    enableMigration,
                    backupBeforeMigration,
                    verbose,
                    internalPathPrefix,
                    charset,
                    langVersion,
                    scope,
                    migrator
            );
        }
    }
}
