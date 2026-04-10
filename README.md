### Update JOOQ schema

1. Change file in `src/main/sql/cache` or `src/msin/sql/library`
2. Run `./gradlew generateJooq`

### Add new file to JOOQ schema

1. Add new file to `src/main/sql/cache` or `src/msin/sql/library`
2. Add file in `build.gradle`
3. Run `./gradlew generateJooq`
4. Add file in `CacheDatabase.java` or `LibraryDatabase.java`

### Build native images

Execute (quick and dirty, mixed with testing configs):
```shell
export JAVA_HOME=~/bin/bellsoft-liberica-vm-full-openjdk21-23.1.1
./gradlew -Pagent clean test --tests '*MainAppTest*'
./gradlew metadataCopy --task test --dir src/main/resources/META-INF/native-image updateNativeImage
./gradlew nativeCompile
```
