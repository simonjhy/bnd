package aQute.bnd.osgi;

import static aQute.bnd.osgi.Constants.BUNDLE_ACTIVATOR;
import static aQute.bnd.osgi.Constants.BUNDLE_CLASSPATH;
import static aQute.bnd.osgi.Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT;
import static aQute.bnd.osgi.Constants.BUNDLE_SYMBOLICNAME;
import static aQute.bnd.osgi.Constants.BUNDLE_VERSION;
import static aQute.bnd.osgi.Constants.CONDITIONAL_PACKAGE;
import static aQute.bnd.osgi.Constants.DYNAMICIMPORT_PACKAGE;
import static aQute.bnd.osgi.Constants.EXPORT_CONTENTS;
import static aQute.bnd.osgi.Constants.EXPORT_PACKAGE;
import static aQute.bnd.osgi.Constants.FAIL_OK;
import static aQute.bnd.osgi.Constants.FRAGMENT_HOST;
import static aQute.bnd.osgi.Constants.IMPORT_PACKAGE;
import static aQute.bnd.osgi.Constants.INCLUDERESOURCE;
import static aQute.bnd.osgi.Constants.INCLUDE_RESOURCE;
import static aQute.bnd.osgi.Constants.PRIVATEPACKAGE;
import static aQute.bnd.osgi.Constants.PRIVATE_PACKAGE;
import static aQute.bnd.osgi.Constants.SOURCES;
import static aQute.bnd.osgi.Constants.WAB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.maven.PomParser;
import aQute.bnd.version.Version;
import aQute.lib.converter.Converter;
import aQute.lib.utf8properties.UTF8Properties;
import aQute.service.reporter.Reporter;

/**
 * This class abstracts domains that have properties holding OSGi meta data. It
 * provides access to the keys, the set method and the get method. It then
 * provides convenient methods to access these properties via semantic methods.
 */
public abstract class Domain implements Iterable<String> {
	final Properties translation = new UTF8Properties();

	public abstract String get(String key);

	public String get(String key, String deflt) {
		String result = get(key);
		if (result != null)
			return result;
		return deflt;
	}

	public String translate(String key) {
		return translate(key, null);
	}

	public String translate(String key, String deflt) {
		String value = get(key);
		if (value == null)
			return deflt;

		if (value.indexOf('%') >= 0) {
			value = value.trim().substring(1);
			return translation.getProperty(value, value);
		}
		return null;
	}

	public abstract void set(String key, String value);

	public abstract Iterator<String> iterator();

	public static Domain domain(final Manifest manifest) {
		Attributes attrs = manifest.getMainAttributes();
		return domain(attrs);
	}

	public static Domain domain(final Attributes attrs) {
		return new Domain() {

			@Override
			public String get(String key) {
				return attrs.getValue(key);
			}

			@Override
			public void set(String key, String value) {
				attrs.putValue(key, value);
			}

			@Override
			public Iterator<String> iterator() {
				final Iterator<Object> it = attrs.keySet().iterator();

				return new Iterator<String>() {

					public boolean hasNext() {
						return it.hasNext();
					}

					public String next() {
						return it.next().toString();
					}

					public void remove() {
						it.remove();
					}
				};
			}
		};
	}

	public static Domain domain(final Processor processor) {
		return new Domain() {

			@Override
			public String get(String key) {
				return processor.getProperty(key);
			}

			@Override
			public String get(String key, String deflt) {
				return processor.getProperty(key, deflt);
			}

			@Override
			public void set(String key, String value) {
				processor.setProperty(key, value);
			}

			@Override
			public Iterator<String> iterator() {
				final Iterator<String> it = processor.getPropertyKeys(true).iterator();

				return new Iterator<String>() {
					String current;

					public boolean hasNext() {
						return it.hasNext();
					}

					public String next() {
						return current = it.next().toString();
					}

					public void remove() {
						processor.getProperties().remove(current);
					}
				};
			}
		};
	}

	public static Domain domain(final Map<String,String> map) {
		return new Domain() {

			@Override
			public String get(String key) {
				return map.get(key);
			}

			@Override
			public void set(String key, String value) {
				map.put(key, value);
			}

			@Override
			public Iterator<String> iterator() {
				return map.keySet().iterator();
			}
		};
	}

	public Parameters getParameters(String key, Reporter reporter) {
		return new Parameters(get(key), reporter);
	}

	public Parameters getParameters(String key) {
		return new Parameters(get(key));
	}

	public Parameters getParameters(String key, String deflt) {
		return new Parameters(get(key, deflt));
	}

	public Parameters getParameters(String key, String deflt, Reporter reporter) {
		return new Parameters(get(key, deflt), reporter);
	}

	public Parameters getRequireBundle() {
		return getParameters(Constants.REQUIRE_BUNDLE);
	}

	public Parameters getImportPackage() {
		return getParameters(IMPORT_PACKAGE);
	}

	public Parameters getExportPackage() {
		return getParameters(EXPORT_PACKAGE);
	}

	public Parameters getBundleClassPath() {
		return getParameters(BUNDLE_CLASSPATH);
	}

	public Parameters getPrivatePackage() {
		Parameters p = getParameters(PRIVATE_PACKAGE);
		p.putAll(getParameters(PRIVATEPACKAGE));
		return p;
	}

	public Parameters getIncludeResource() {
		Parameters ic = getParameters(INCLUDE_RESOURCE);
		ic.putAll(getParameters(INCLUDERESOURCE));
		ic.putAll(getParameters(WAB));
		return ic;
	}

	public Parameters getDynamicImportPackage() {
		return getParameters(DYNAMICIMPORT_PACKAGE);
	}

	public Parameters getExportContents() {
		return getParameters(EXPORT_CONTENTS);
	}

	public String getBundleActivator() {
		return get(BUNDLE_ACTIVATOR);
	}

	public void setPrivatePackage(String s) {
		if (s != null)
			set(PRIVATEPACKAGE, s);
	}

	public void setIncludeResource(String s) {
		if (s != null)
			set(INCLUDE_RESOURCE, s);
	}

	public void setBundleActivator(String s) {
		if (s != null)
			set(BUNDLE_ACTIVATOR, s);
	}

	public void setExportPackage(String s) {
		if (s != null)
			set(EXPORT_PACKAGE, s);
	}

	public void setImportPackage(String s) {
		if (s != null)
			set(IMPORT_PACKAGE, s);
	}

	public void setBundleClasspath(String s) {
		if (s != null)
			set(BUNDLE_CLASSPATH, s);
	}

	public Parameters getBundleClasspath() {
		return getParameters(BUNDLE_CLASSPATH);
	}

	public void setBundleRequiredExecutionEnvironment(String s) {
		if (s != null)
			set(BUNDLE_REQUIREDEXECUTIONENVIRONMENT, s);
	}

	public Parameters getBundleRequiredExecutionEnvironment() {
		return getParameters(BUNDLE_REQUIREDEXECUTIONENVIRONMENT);
	}

	public void setSources(boolean b) {
		if (b)
			set(SOURCES, "true");
		else
			set(SOURCES, "false");
	}

	public boolean isSources() {
		return Processor.isTrue(get(SOURCES));
	}

	public Map.Entry<String,Attrs> getBundleSymbolicName() {
		Parameters p = getParameters(BUNDLE_SYMBOLICNAME);
		if (p.isEmpty())
			return null;
		return p.entrySet().iterator().next();
	}

	public Map.Entry<String,Attrs> getFragmentHost() {
		Parameters p = getParameters(FRAGMENT_HOST);
		if (p.isEmpty())
			return null;
		return p.entrySet().iterator().next();
	}

	public void setBundleSymbolicName(String s) {
		set(BUNDLE_SYMBOLICNAME, s);
	}

	public String getBundleVersion() {
		return get(BUNDLE_VERSION);
	}

	public void setBundleVersion(String version) {
		Version v = new Version(version);
		set(BUNDLE_VERSION, v.toString());
	}

	public void setBundleVersion(Version version) {
		set(BUNDLE_VERSION, version.toString());
	}

	public void setFailOk(boolean b) {
		set(FAIL_OK, b + "");
	}

	public void setRunfw(String runfw) {
		set(Constants.RUNFW, runfw);
	}

	public void setRunRequires(String runRq) {
		set(Constants.RUNREQUIRES, runRq);
	}

	public void setAugment(String augments) {
		set(Constants.AUGMENT, augments);
	}

	/**
	 * Indicates that this run should ignore errors and succeed anyway
	 * 
	 * @return true if this processor should return errors
	 */
	public boolean isFailOk() {
		return Processor.isTrue(get(FAIL_OK));
	}

	/**
	 * Find an icon with the requested size in the list of icons.
	 * 
	 * @param requestedSize the number of pixels desired
	 * @return null or a the selected URI (which may be relative)
	 */
	public String getIcon(int requestedSize) throws Exception {
		String spec = get(Constants.BUNDLE_ICON);
		if (spec == null)
			return null;

		Parameters p = OSGiHeader.parseHeader(spec);
		int dist = Integer.MAX_VALUE;
		String selected = null;

		for (Entry<String,Attrs> e : p.entrySet()) {
			String url = e.getKey();
			if (selected == null)
				selected = url;
			if (e.getValue() != null) {
				String s = e.getValue().get("size");
				if (s != null) {
					int size = Converter.cnv(Integer.class, s);
					if (size != 0 && Math.abs(requestedSize - size) < dist) {
						dist = Math.abs(requestedSize - size);
						selected = url;
					}
				}
			}
		}
		return selected;
	}

	public void setConditionalPackage(String string) {
		set(CONDITIONAL_PACKAGE, string);

	}

	public void setRunblacklist(String blacklist) {
		set(Constants.RUNBLACKLIST, blacklist);
	}

	public String getRunblacklist() {
		return get(Constants.RUNBLACKLIST);
	}

	public void setRunee(String string) {
		set(Constants.RUNEE, string);
	}

	public String getRunee() {
		return get(Constants.RUNEE);
	}

	public void setTranslation(Jar jar) throws Exception {

		Manifest m = jar.getManifest();
		if (m == null)
			return;

		String path = m.getMainAttributes().getValue(Constants.BUNDLE_LOCALIZATION);
		if (path == null)
			path = org.osgi.framework.Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME;

		path += ".properties";

		Resource propsResource = jar.getResource(path);
		if (propsResource != null) {
			InputStream in = propsResource.openInputStream();
			try {
				translation.load(in);
			} finally {
				in.close();
			}
		}
	}

	public Parameters getRequireCapability() {
		return getParameters(Constants.REQUIRE_CAPABILITY);
	}

	public Parameters getProvideCapability() {
		return getParameters(Constants.PROVIDE_CAPABILITY);
	}

	public static Domain domain(File file) throws IOException {
		try (FileInputStream in = new FileInputStream(file);) {

			if (file.getName().endsWith(".mf")) {
				Manifest m = new Manifest(in);
				return domain(m);
			}

			if (file.getName().endsWith(".properties") || file.getName().endsWith(".bnd")) {
				Processor p = new Processor();
				p.setProperties(file);
				return domain(p);
			}

			if (file.getName().endsWith(".pom")) {
				try {
					PomParser p = new PomParser();
					p.setProperties(p.getProperties(file));
					return domain(p);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}

			// default & last. Assume JAR

			try (JarInputStream jin = new JarInputStream(in);) {
				Manifest m = jin.getManifest();
				if (m != null)
					return domain(m);
			}
		}

		// BUT WAIT! Maybe it's just a zip file (bad jar, bad jar...)

		try (ZipFile zf = new ZipFile(file)) {
			ZipEntry entry = zf.getEntry("META-INF/MANIFEST.MF");
			if (entry == null)
				return null;
			Manifest m = new Manifest(zf.getInputStream(entry));
			return domain(m);
		} catch (ZipException e) {
			return null;
		}
	}

	public String getBundleName() {
		return get(Constants.BUNDLE_NAME);
	}

	public String getBundleDescription() {
		return get(Constants.BUNDLE_DESCRIPTION);
	}

	public String getBundleCopyright() {
		return get(Constants.BUNDLE_COPYRIGHT);
	}

	public String getBundleDocURL() {
		return get(Constants.BUNDLE_COPYRIGHT);
	}

	public String getBundleVendor() {
		return get(Constants.BUNDLE_VENDOR);
	}

	public String getBundleContactAddress() {
		return get(Constants.BUNDLE_CONTACTADDRESS);
	}

	public String getBundleCategory() {
		return get(Constants.BUNDLE_CATEGORY);
	}

	public String getBundleNative() {
		return get(Constants.BUNDLE_NATIVECODE);
	}
}
