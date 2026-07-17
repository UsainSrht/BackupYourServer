package me.usainsrht.backupyourserver.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public final class BackupYourServerLoader implements PluginLoader {

    @Override
    public void classloader(final @NotNull PluginClasspathBuilder classpathBuilder) {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "papermc",
                "default",
                "https://repo.papermc.io/repository/maven-public/"
        ).build());

        resolver.addRepository(new RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());
/*
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:adventure-api:5.2.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:adventure-text-minimessage:4.23.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:adventure-text-serializer-plain:4.23.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:adventure-key:4.23.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:examination-api:1.3.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("net.kyori:examination-string:1.3.0"), null));
*/
        classpathBuilder.addLibrary(resolver);
    }
}
