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

package com.liferay.apio.architect.sample.liferay.portal.rating;

import aQute.bnd.annotation.ProviderType;

import com.liferay.apio.architect.sample.liferay.portal.identifier.AggregateRatingIdentifier;

/**
 * Provides the service for getting an {@link AggregateRating}.
 *
 * @author Alejandro Hernández
 */
@ProviderType
public interface AggregateRatingService {

	/**
	 * Returns the aggregate rating specified by the aggregate rating
	 * identifier.
	 *
	 * @param  aggregateRatingIdentifier the aggregate rating's identifier
	 * @return the aggregate rating
	 */
	public AggregateRating getAggregateRating(
		AggregateRatingIdentifier aggregateRatingIdentifier);

}