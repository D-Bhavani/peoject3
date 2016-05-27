package ru.ftc.upc.testing.dropper;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

/**
 * Created by Plizga on 29.04.2016 12:50
 */
class PatchingTransformer implements ClassFileTransformer {
  private static final Logger log = LoggerFactory.getLogger(PatchingTransformer.class);

  private final DropletsMap dropletMap = new DropletsMap();
  private final ClassPool pool;

  PatchingTransformer(Set<Droplet> droplets) {
    pool = ClassPool.getDefault();

    for (Droplet droplet : droplets) {
      String jvmClazzFqName = droplet.getClazz().replaceAll("\\.", "/");
      dropletMap.put(jvmClazzFqName, droplet);
    }
  }

  @Override
  public byte[] transform(ClassLoader loader,
                          String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain,
                          byte[] classFileBuffer) throws IllegalClassFormatException {
    if (!dropletMap.containsKey(className)) {
      return null;
    }
    try {
      return applyDroplets(dropletMap.get(className), classFileBuffer);

    } catch (Exception e) {
      log.error(format("Failed to patch class '%s'. Class skipped.", className), e);
      return null;
    }
  }

  private byte[] applyDroplets(Set<Droplet> droplets, byte[] classFileBuffer) throws Exception {
    String className = droplets.iterator().next().getClazz();
    pool.insertClassPath(new ByteArrayClassPath(className, classFileBuffer));
    CtClass ctClass = pool.get(className);
    if (ctClass.isFrozen())
      throw new IllegalStateException(format("Class '%s' is frozen.", ctClass.getName()));

    for (Droplet droplet : droplets) {
      CtMethod ctMethod = ctClass.getDeclaredMethod(droplet.getMethod());

      Cutpoint cutpoint = droplet.getCutpoint();
      MethodPatcher patcher = cutpoint.patcherClass.newInstance();

      patcher.apply(ctMethod, droplet);
      log.info("Method {} of class {} has been patched with {}.", ctMethod.getName(), ctClass.getName(),
              patcher.getClass().getSimpleName());
    }
    return ctClass.toBytecode();
  }

  static class DropletsMap extends HashMap<String, Set<Droplet>> {

    void put(String key, Droplet value) {
      Set<Droplet> droplets = this.get(key);
      if (droplets == null) {
        droplets = new HashSet<Droplet>();
        super.put(key, droplets);
      }
      droplets.add(value);
    }
  }
}
