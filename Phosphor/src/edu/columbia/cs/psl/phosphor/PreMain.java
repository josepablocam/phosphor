package edu.columbia.cs.psl.phosphor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.List;

import edu.columbia.cs.psl.phosphor.instrumenter.TaintTrackingClassVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.runtime.TaintInstrumented;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.Tainted;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteArrayWithSingleObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithIntTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;

public class PreMain {
	private static Instrumentation instrumentation;

	static final boolean DEBUG = false;
	public static boolean RUNTIME_INST = false;

	public static ClassLoader bigLoader = PreMain.class.getClassLoader();

	public static final class TaintTrackingClassTransformer implements ClassFileTransformer {
		private final class HackyClassWriter extends ClassWriter {

			private HackyClassWriter(ClassReader classReader, int flags) {
				super(classReader, flags);
			}

			private Class<?> getClass(String name) throws ClassNotFoundException {
				if(RUNTIME_INST)
					throw new ClassNotFoundException();
				try {
					return Class.forName(name.replace("/", "."), false, bigLoader);
				} catch (SecurityException e) {
					throw new ClassNotFoundException("Security exception when loading class");
				} catch (NoClassDefFoundError e) {
					throw new ClassNotFoundException();
				} catch (Throwable e) {
					throw new ClassNotFoundException();
				}
			}

			protected String getCommonSuperClass(String type1, String type2) {
				Class<?> c, d;
				try {
					c = getClass(type1);
					d = getClass(type2);
				} catch (ClassNotFoundException e) {
					//					System.err.println("Can not do superclass for " + type1 + " and " + type2);
					//					        	logger.debug("Error while finding common super class for " + type1 +"; " + type2,e);
					return "java/lang/Object";
					//					        	throw new RuntimeException(e);
				} catch (ClassCircularityError e) {
					return "java/lang/Object";
				}
				if (c.isAssignableFrom(d)) {
					return type1;
				}
				if (d.isAssignableFrom(c)) {
					return type2;
				}
				if (c.isInterface() || d.isInterface()) {
					return "java/lang/Object";
				} else {
					do {
						c = c.getSuperclass();
					} while (!c.isAssignableFrom(d));
					//					System.out.println("Returning " + c.getName());
					return c.getName().replace('.', '/');
				}
			}
		}

		static boolean innerException = false;
		static boolean INITED = false;

		public TaintedByteArrayWithObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint[] classtaint,
				byte[] classfileBuffer, TaintedByteArrayWithObjTag ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = true;
				Configuration.init();
				INITED = true;
			}
			ret.val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			ret.taint = null;
			Configuration.taintTagFactory.instrumentationEnding(className);

			return ret;
		}
		public TaintedByteArrayWithObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint[] classtaint,
				byte[] classfileBuffer, Object[] ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = true;
				Configuration.init();
				INITED = true;
			}
			if (className.startsWith("sun")) //there are dynamically generated accessors for reflection, we don't want to instrument those.
				((TaintedByteArrayWithObjTag) ret[9]).val = classfileBuffer;
			else
				((TaintedByteArrayWithObjTag) ret[9]).val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			((TaintedByteArrayWithObjTag) ret[9]).taint = new Taint[((TaintedByteArrayWithObjTag) ret[9]).val.length];
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ((TaintedByteArrayWithObjTag)ret[9]);
		}


		public TaintedByteArrayWithObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint[] classtaint,
				byte[] classfileBuffer, ControlTaintTagStack ctrl, TaintedByteArrayWithObjTag ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = true;
				Configuration.MULTI_TAINTING = true;
				Configuration.init();
				INITED = true;
			}
			ret.val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			ret.taint = null;
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ret;
		}
		
		public TaintedByteArrayWithSingleObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint classtaint,
				byte[] classfileBuffer, TaintedByteArrayWithSingleObjTag ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = true;
				Configuration.SINGLE_TAG_PER_ARRAY = true;
				Configuration.init();
				INITED = true;
			}
			ret.val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			ret.taint = null;
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ret;
		}
		
		public TaintedByteArrayWithSingleObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint classtaint,
				byte[] classfileBuffer, Object[] ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = true;
				Configuration.SINGLE_TAG_PER_ARRAY = true;
				Configuration.init();
				INITED = true;
			}
			((TaintedByteArrayWithSingleObjTag)ret[TaintUtils.PREALLOC_BYTEARRAY]).val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			((TaintedByteArrayWithSingleObjTag)ret[TaintUtils.PREALLOC_BYTEARRAY]).taint = null;
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ((TaintedByteArrayWithSingleObjTag)ret[TaintUtils.PREALLOC_BYTEARRAY]);
		}
		
		public TaintedByteArrayWithSingleObjTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, Taint classtaint,
				byte[] classfileBuffer, ControlTaintTagStack ctrl, TaintedByteArrayWithSingleObjTag ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = true;
				Configuration.MULTI_TAINTING = true;
				Configuration.SINGLE_TAG_PER_ARRAY = true;
				Configuration.init();
				INITED = true;
			}
			ret.val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			ret.taint = null;
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ret;
		}

		public TaintedByteArrayWithIntTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, int[] classtaint,
				byte[] classfileBuffer, Object[] ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = false;
				Configuration.init();
				INITED = true;
			}
			if (className.startsWith("sun")) //there are dynamically generated accessors for reflection, we don't want to instrument those.
				((TaintedByteArrayWithIntTag) ret[9]).val = classfileBuffer;
			else
				((TaintedByteArrayWithIntTag) ret[9]).val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			((TaintedByteArrayWithIntTag) ret[9]).taint = new int[((TaintedByteArrayWithIntTag) ret[9]).val.length];
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ((TaintedByteArrayWithIntTag)ret[9]);
		}
		
		public TaintedByteArrayWithIntTag transform$$PHOSPHORTAGGED(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, int[] classtaint,
				byte[] classfileBuffer, TaintedByteArrayWithIntTag ret) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = false;
				Configuration.init();
				INITED = true;
			}
			if (className.startsWith("sun")) //there are dynamically generated accessors for reflection, we don't want to instrument those.
				ret.val = classfileBuffer;
			else
				ret.val = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			ret.taint = new int[ret.val.length];
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ret;
		}

		public byte[] transform$$PHOSPHORUNTAGGED(ClassLoader loader, final String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer,
				Object[] prealloc) throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className);

			if (!INITED) {
				Configuration.IMPLICIT_TRACKING = false;
				Configuration.MULTI_TAINTING = false;
				Configuration.init();
				INITED = true;
			}
			byte[] ret;
			if (className.startsWith("sun")) //there are dynamically generated accessors for reflection, we don't want to instrument those.
				ret = classfileBuffer;
			else
				ret = transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			Configuration.taintTagFactory.instrumentationEnding(className);
			return ret;
		}
		public byte[] transform(ClassLoader loader, final String className2, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
				throws IllegalClassFormatException {
			Configuration.taintTagFactory.instrumentationStarting(className2);
			byte[] ret = _transform(loader, className2, classBeingRedefined, protectionDomain, classfileBuffer);
			Configuration.taintTagFactory.instrumentationEnding(className2);
			return ret;
		}

		MessageDigest md5inst;

		private byte[] _transform(ClassLoader loader, final String className2, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
				throws IllegalClassFormatException {
			ClassReader cr = new ClassReader(classfileBuffer);
			String className = cr.getClassName();
			innerException = false;
//			bigLoader = loader;
//			Instrumenter.loader = bigLoader;
			if (Instrumenter.isIgnoredClass(className)) {
				//				System.out.println("Premain.java ignore: " + className);
				return classfileBuffer;
			}
			
			ClassNode cn = new ClassNode();
			cr.accept(cn, ClassReader.SKIP_CODE);
			boolean skipFrames = false;
			if (cn.version >= 100 || cn.version <= 50 || className.endsWith("$Access4JacksonSerializer") || className.endsWith("$Access4JacksonDeSerializer"))
				skipFrames = true;
			if (cn.visibleAnnotations != null)
				for (Object o : cn.visibleAnnotations) {
					AnnotationNode an = (AnnotationNode) o;
					if (an.desc.equals(Type.getDescriptor(TaintInstrumented.class))) {
						return classfileBuffer;
					}
				}
			if (cn.interfaces != null)
				for (Object s : cn.interfaces) {
					if (s.equals(Type.getInternalName(TaintedWithObjTag.class)) || s.equals(Type.getInternalName(TaintedWithIntTag.class))) {
						return classfileBuffer;
					}
				}
			for (Object mn : cn.methods)
				if (((MethodNode) mn).name.equals("getPHOSPHOR_TAG")) {
					return classfileBuffer;
				}
			if (Configuration.CACHE_DIR != null) {
				String cacheKey = className.replace("/", ".");
				File f = new File(Configuration.CACHE_DIR + File.separator + cacheKey + ".md5sum");
				if (f.exists()) {
					try {
						FileInputStream fis = new FileInputStream(f);
						byte[] cachedDigest = new byte[1024];
						fis.read(cachedDigest);
						fis.close();
						if (md5inst == null)
							md5inst = MessageDigest.getInstance("MD5");
						byte[] checksum = md5inst.digest(classfileBuffer);
						boolean matches = true;
						if (checksum.length > cachedDigest.length)
							matches = false;
						if (matches)
							for (int i = 0; i < checksum.length; i++) {
								if (checksum[i] != cachedDigest[i]) {
									matches = false;
									break;
								}
							}
						if (matches) {
							byte[] ret = Files.readAllBytes(new File(Configuration.CACHE_DIR + File.separator + cacheKey + ".class").toPath());
							return ret;
						}
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
			List<FieldNode> fields = cn.fields;
			if (skipFrames) {
				cn = null;
				//This class is old enough to not guarantee frames. Generate new frames for analysis reasons, then make sure to not emit ANY frames.
				ClassWriter cw = new HackyClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
				cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
					@Override
					public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
						return new JSRInlinerAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc, signature, exceptions);
					}
				}, 0);
				cr = new ClassReader(cw.toByteArray());
			}
			//			System.out.println("Instrumenting: " + className);
			//			System.out.println(classBeingRedefined);
			//Find out if this class already has frames
			TraceClassVisitor cv = null;
			try {

				ClassWriter cw = new HackyClassWriter(cr, ClassWriter.COMPUTE_MAXS);
				ClassVisitor _cv = cw;
				if (Configuration.extensionClassVisitor != null) {
					Constructor<? extends ClassVisitor> extra = Configuration.extensionClassVisitor.getConstructor(ClassVisitor.class, Boolean.TYPE);
					_cv = extra.newInstance(_cv, skipFrames);
				}
				if(TaintUtils.VERIFY_CLASS_GENERATION)
					_cv = new CheckClassAdapter(_cv, false);
				_cv = new SerialVersionUIDAdder(new TaintTrackingClassVisitor(_cv, skipFrames, fields));
				if (Configuration.WITH_SELECTIVE_INST)
					cr.accept(new PartialInstrumentationInferencerCV(), ClassReader.EXPAND_FRAMES);
				cr.accept(
				//							new CheckClassAdapter(
						_cv
						//									)
						, ClassReader.EXPAND_FRAMES);

				if (DEBUG) {
					File debugDir = new File("debug");
					if (!debugDir.exists())
						debugDir.mkdir();
					File f = new File("debug/" + className.replace("/", ".") + ".class");
					FileOutputStream fos = new FileOutputStream(f);
					fos.write(cw.toByteArray());
					fos.close();
				}
				{
					//					if(TaintUtils.DEBUG_FRAMES)
					//						System.out.println("NOW IN CHECKCLASSADAPTOR");
					if (DEBUG
							|| (TaintUtils.VERIFY_CLASS_GENERATION && !className.startsWith("org/codehaus/janino/UnitCompiler")
									&& !className.startsWith("jersey/repackaged/com/google/common/cache/LocalCache")
									&& !className.startsWith("jersey/repackaged/com/google/common/collect/AbstractMapBasedMultimap") && !className
										.startsWith("jersey/repackaged/com/google/common/collect/"))) {
						ClassReader cr2 = new ClassReader(cw.toByteArray());
						cr2.accept(new CheckClassAdapter(new ClassWriter(0)), 0);
					}
				}
				//				cv= new TraceClassVisitor(null,null);
				//				try{
				//					System.err.println("WARN LOGGING CLASS TO ASCII");
				//					cr = new ClassReader(cw.toByteArray());
				//					cr.accept(
				////							new CheckClassAdapter(
				//									cv	
				////									)
				//							, 0);
				//					PrintWriter pw = null;
				//					try {
				//						pw = new PrintWriter(new FileWriter("lastClass.txt"));
				//					} catch (IOException e) {
				//						// TODO Auto-generated catch block
				//						e.printStackTrace();
				//					}
				//					cv.p.print(pw);
				//					pw.flush();
				//				}
				//				catch(Throwable t)
				//				{
				//					t.printStackTrace();
				//				}
				//				System.out.println("Succeeded w " + className);
				if (Configuration.CACHE_DIR != null) {
					String cacheKey = className.replace("/", ".");
					File f = new File(Configuration.CACHE_DIR + File.separator + cacheKey + ".class");
					FileOutputStream fos = new FileOutputStream(f);
					byte[] ret = cw.toByteArray();
					fos.write(ret);
					fos.close();
					if (md5inst == null)
						md5inst = MessageDigest.getInstance("MD5");
					byte[] checksum = md5inst.digest(classfileBuffer);
					f = new File(Configuration.CACHE_DIR + File.separator + cacheKey + ".md5sum");
					fos = new FileOutputStream(f);

					fos.write(checksum);
					fos.close();
					return ret;
				}
				return cw.toByteArray();
			} catch (Throwable ex) {
				ex.printStackTrace();
				cv = new TraceClassVisitor(null, null);
				try {
					ClassVisitor _cv = cv;
					if (Configuration.extensionClassVisitor != null) {
						Constructor<? extends ClassVisitor> extra = Configuration.extensionClassVisitor.getConstructor(ClassVisitor.class, Boolean.TYPE);
						_cv = extra.newInstance(_cv, skipFrames);
					}
					_cv = new SerialVersionUIDAdder(new TaintTrackingClassVisitor(_cv, skipFrames, fields));

					cr.accept(_cv, ClassReader.EXPAND_FRAMES);
				} catch (Throwable ex2) {
				}
				ex.printStackTrace();
				System.err.println("method so far:");
				if (!innerException) {
					PrintWriter pw = null;
					try {
						pw = new PrintWriter(new FileWriter("lastClass.txt"));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					cv.p.print(pw);
					pw.flush();
				}
				System.out.println("Saving " + className);
				File f = new File("debug/" + className.replace("/", ".") + ".class");
				try {
					FileOutputStream fos = new FileOutputStream(f);
					fos.write(classfileBuffer);
					fos.close();
				} catch (Exception ex2) {
					ex.printStackTrace();
				}
				System.exit(-1);
				return new byte[0];

			}
		}
	}

	public static void premain$$PHOSPHORTAGGED(String args, Instrumentation inst, ControlTaintTagStack ctrl) {
		Configuration.IMPLICIT_TRACKING = true;
		Configuration.MULTI_TAINTING = true;
		TaintUtils.PREALLOC_RETURN_ARRAY = false;
		Configuration.init();
		premain(args, inst);
	}
	public static void premain$$PHOSPHORTAGGED(String args, Instrumentation inst, Object[] prealloc) {
		Configuration.init();
		premain(args, inst);
	}

	public static void premain(String args, Instrumentation inst) {
		instrumentation = inst;
		RUNTIME_INST = true;
		if (args != null) {
			String[] aaa = args.split(",");
			for (String s : aaa) {
				if (s.equals("acmpeq"))
					Configuration.WITH_UNBOX_ACMPEQ = true;
				else if (s.equals("enum"))
					Configuration.WITH_ENUM_BY_VAL = true;
				else if (s.startsWith("cacheDir=")) {
					Configuration.CACHE_DIR = s.substring(9);
					File f = new File(Configuration.CACHE_DIR);
					if (!f.exists())
						f.mkdir();
				}
				else if(s.startsWith("withSelectiveInst="))
				{
					Configuration.WITH_SELECTIVE_INST=true;
					Configuration.selective_inst_config = s.substring(18);
					SelectiveInstrumentationManager.populateMethodsToInstrument(Configuration.selective_inst_config);
				}
				else if(s.equals("singlearraytag"))
					Configuration.SINGLE_TAG_PER_ARRAY = true;
			}
		}
		Configuration.init();
		if (Instrumenter.loader == null)
			Instrumenter.loader = bigLoader;
		ClassFileTransformer transformer = new TaintTrackingClassTransformer();
		inst.addTransformer(transformer);

	}

	public static Instrumentation getInstrumentation() {
		return instrumentation;
	}
}
