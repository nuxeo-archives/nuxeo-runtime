/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     "Stephane Lacoin (aka matic) slacoin@nuxeo.com"
 */
package org.nuxeo.runtime.management.metrics;

import java.util.Map;

/**
 * @author "Stephane Lacoin (aka matic) slacoin@nuxeo.com"
 *
 */
public interface MetricAttributes {

    void clear();

    void put(String key, Object value);

    void putAll(Map<String,Object> values);

    Map<String,String> get();

}