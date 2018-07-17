# Contribute to Swift-WALA


## Download Projects

```
mkdir swift-source
cd swift-source
git clone https://github.com/apple/swift
git clone https://github.com/wala/WALA
git clone https://github.com/themaplelab/swift-wala
```

## Build Dependencies


### WALA

```
cd ./WALA
./gradlew assemble
cd ..
```

### Swift

```
cd ./swift
./utils/update-checkout --clone --tag swift-4.1.1-RELEASE
./utils/build-script -d
cd ..
```


### Edit Swift-WALA Configurations

```
cd swift-wala/com.ibm.wala.cast.swift
cp gralde.properties.example gradle.properties
```

Edit `gralde.properties` and provide proper paths.
