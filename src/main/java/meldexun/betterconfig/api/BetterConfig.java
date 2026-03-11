package meldexun.betterconfig.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BetterConfig {

	String modid();

	String name() default "";

	String category() default "general";

	boolean lowerCaseCategories() default true;

	boolean bigCategoryComments() default true;

	boolean addRangesToComments() default true;

	boolean addDefaultsToComments() default true;

	boolean removeDeprecatedEntries() default false;

	/**
	 * Defines the ordering for elements in this configuration.
	 * <p>
	 * Comparators are applied sequentially. If a comparator considers two elements equal, the next comparator is used to determine the order.
	 * </p>
	 * 
	 * <strong>Possible values:</strong>
	 * <ul>
	 * <li>{@link ConfigComparator#EXPLICIT EXPLICIT} – Orders elements by the value of their {@link Order @Order} annotation.</li>
	 * <li>{@link ConfigComparator#CATEGORIES_FIRST CATEGORIES_FIRST} – Category elements come first.</li>
	 * <li>{@link ConfigComparator#CATEGORIES_LAST CATEGORIES_LAST} – Category elements come last.</li>
	 * <li>{@link ConfigComparator#NON_MAP_CATEGORIES_FIRST NON_MAP_CATEGORIES_FIRST} – Non-map-category elements come first.</li>
	 * <li>{@link ConfigComparator#NON_MAP_CATEGORIES_LAST NON_MAP_CATEGORIES_LAST} – Non-map-category elements come last.</li>
	 * <li>{@link ConfigComparator#NAME_CASE_SENSITIVE NAME_CASE_SENSITIVE} – Orders elements by comparing their name lexicographically, case-sensitively.</li>
	 * <li>{@link ConfigComparator#NAME_CASE_INSENSITIVE NAME_CASE_INSENSITIVE} – Orders elements by comparing their name lexicographically, ignoring case.</li>
	 * <li>{@link ConfigComparator#INITIALIZATION INITIALIZATION} – <strong>WARNING, READ CAREFULLY!</strong> Attempts to order elements by their initialization order. The JVM does not provide a guaranteed way to retrieve field initialization order at runtime. BetterConfig analyzes the class bytecode to approximate this order. While this works in most cases, correctness and stability are not guaranteed.</li>
	 * </ul>
	 */
	ConfigComparator[] elementOrder() default { ConfigComparator.EXPLICIT, ConfigComparator.CATEGORIES_LAST, ConfigComparator.NAME_CASE_SENSITIVE };

	enum ConfigComparator {
		EXPLICIT,
		CATEGORIES_FIRST,
		CATEGORIES_LAST,
		NON_MAP_CATEGORIES_FIRST,
		NON_MAP_CATEGORIES_LAST,
		NAME_CASE_SENSITIVE,
		NAME_CASE_INSENSITIVE,
		INITIALIZATION
	}

}
