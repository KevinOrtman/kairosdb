/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.kairosdb.core.aggregator;

import org.kairosdb.core.DataPoint;
import org.kairosdb.core.datastore.DataPointGroup;
import org.kairosdb.core.datastore.Sampling;
import org.kairosdb.core.http.rest.json.RelativeTime;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class RangeAggregator extends SortedAggregator
{
	private long m_startTime;
	private long m_range = 1L;

	@Override
	protected DataPointGroup aggregate(DataPointGroup dataPointGroup)
	{
		checkNotNull(dataPointGroup);

		return (new RangeDataPointAggregator(dataPointGroup, getSubAggregator()));
	}

	public void setSampling(Sampling sampling)
	{
		//Todo: make this smarter to handle months
		m_range = sampling.getSampling();
	}

	/**
	 Start time to calculate the ranges from.  Typically this is the start
	 of the query
	 @param startTime
	 */
	public void setStartTime(long startTime)
	{
		m_startTime = startTime;
	}


	/**
	 Return a RangeSubAggregator that will be used to aggregate data over a
	 discrete range of data points.  This is called once per grouped data series.

	 For example, if one metric is queried and no grouping is done this method is
	 called once and the resulting object is called over and over for each range
	 within the results.

	 If the query were grouping by the host tag and host has values of 'A' and 'B'
	 this method will be called twice, once to aggregate results for 'A' and once
	 to aggregate results for 'B'.
	 @return
	 */
	protected abstract RangeSubAggregator getSubAggregator();


	//===========================================================================
	/**

	 */
	private class RangeDataPointAggregator extends AggregatedDataPointGroupWrapper
	{
		private RangeSubAggregator m_subAggregator;

		public RangeDataPointAggregator(DataPointGroup innerDataPointGroup,
				RangeSubAggregator subAggregator)
		{
			super(innerDataPointGroup);
			m_subAggregator = subAggregator;
		}

		private long getRange(long timestamp)
		{
			return ((int)((timestamp - m_startTime) / m_range));
		}

		@Override
		public DataPoint next()
		{
			SubRangeIterator subIterator = new SubRangeIterator(
					getRange(currentDataPoint.getTimestamp()));

			DataPoint ret = m_subAggregator.getNextDataPoint(currentDataPoint.getTimestamp(),
					subIterator);

			return (ret);
		}

		//========================================================================
		/**
		 This class provides an iterator over a discrete range of data points
		 */
		private class SubRangeIterator implements Iterator<DataPoint>
		{
			private long m_currentRange;

			public SubRangeIterator(long range)
			{
				m_currentRange = range;
			}

			@Override
			public boolean hasNext()
			{
				return ((currentDataPoint != null) && (getRange(currentDataPoint.getTimestamp()) == m_currentRange));
			}

			@Override
			public DataPoint next()
			{
				DataPoint ret = currentDataPoint;
				if (hasNextInternal())
					currentDataPoint = nextInternal();

				return (ret);
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		}
	}

	//===========================================================================
	/**
	 Instances of this object are created once per grouped data series.
	 */
	public interface RangeSubAggregator
	{
		/**
		 Returns an aggregated data point from a ragne that is passed in
		 as dataPointRange.
		 @param returnTime Timestamp to use on return data point.  This is currently
		                   passing the timestamp of the first data point in the range.
		 @param dataPointRange Range to aggregate over.
		 @return
		 */
		public DataPoint getNextDataPoint(long returnTime, Iterator<DataPoint> dataPointRange);
	}
}
