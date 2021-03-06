/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.apio.architect.test.form;

import static org.hamcrest.core.Is.is;

import com.liferay.apio.architect.form.Form;
import com.liferay.apio.architect.test.form.FormConditions.Builder;
import com.liferay.apio.architect.test.internal.form.IsReturnedIn;

import java.util.function.Function;

import org.hamcrest.Matcher;

/**
 * Provides {@link Matcher} objects that can be used in testing {@code Forms}.
 *
 * <p>
 * This class shouldn't be instantiated.
 * </p>
 *
 * @author Alejandro Hernández
 */
public class FormMatchers {

	/**
	 * Checks if a {@code Form} is indeed a valid {@code Form} that follows
	 * certain conditions.
	 *
	 * @param  function a function that receives a {@link
	 *         FormConditions.Builder} and returns the constructed {@link
	 *         FormConditions}
	 * @return the matcher that checks if the form is valid
	 * @review
	 */
	public static <T> Matcher<Form<T>> isAFormWithConditions(
		Function<Builder<T>, FormConditions<T>> function) {

		return function.apply(new Builder<>());
	}

	/**
	 * Returns a function that receives an object and returns a {@code Matcher}
	 * for a {@code Form} part.
	 *
	 * @param  function a function that takes an instance of the form and
	 *         returns something
	 * @return a function that receives an object and returns a matcher for a
	 *         form part
	 * @review
	 */
	public static <T> Function<Object, Matcher<T>> isReturnedIn(
		Function<T, ?> function) {

		return object -> new IsReturnedIn<>(function, is(object));
	}

	private FormMatchers() {
		throw new UnsupportedOperationException();
	}

}