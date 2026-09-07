import fs from 'node:fs/promises';
const dir=new URL('./audit/runtime/',import.meta.url); await fs.mkdir(dir,{recursive:true});
const files={
 'spine-sources.jar':'https://repo.maven.apache.org/maven2/com/esotericsoftware/spine/spine-android/4.2.12/spine-android-4.2.12-sources.jar',
 'spine.pom':'https://repo.maven.apache.org/maven2/com/esotericsoftware/spine/spine-android/4.2.12/spine-android-4.2.12.pom',
 'filament-sources.jar':'https://repo.maven.apache.org/maven2/com/google/android/filament/filament-utils-android/1.75.1/filament-utils-android-1.75.1-sources.jar',
 'spine-license.txt':'https://raw.githubusercontent.com/EsotericSoftware/spine-runtimes/4.2/LICENSE',
};
for(const [name,url] of Object.entries(files)) {
 const response=await fetch(url,{signal:AbortSignal.timeout(30000)}); if(!response.ok)throw Error(`${name} ${response.status}`);
 await fs.writeFile(new URL(name,dir),Buffer.from(await response.arrayBuffer()));console.log(name);
}
