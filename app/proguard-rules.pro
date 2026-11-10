# The screening service, receivers and jobs are referenced from the manifest
# and kept automatically. Nothing is loaded by reflection.
-dontobfuscate
# The history stores verdicts, categories and reasons by enum name.
-keep enum io.github.dabaneghassan.chut.engine.** { *; }
