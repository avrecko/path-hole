/*
 * Copyright 2012 Alen Vre\u010Dko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.typesafe.path_hole

import org.scalatest.{BeforeAndAfter, GivenWhenThen, FeatureSpec}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.logging.{StreamHandler, SimpleFormatter, Logger}


/**
 * Tests the functionality of the Path Hole agent.
 *
 * @author Alen Vre\u010Dko
 */
@RunWith(classOf[JUnitRunner])
class PathHoleTest extends FeatureSpec with GivenWhenThen with BeforeAndAfter {

  feature("Classes on the classpath may be made unavailable by path-hole") {

    info("I want to be able to see classes as normal")
    info("but classes that match the path-hole agent filter should be unavailable")
    info("by unavailable I mean ClassNotFoundException should be thrown")
    info("the filter is defined by using System property -Dboot_hole.filter=...")

    val thisPackage = this.getClass.getPackage.getName

    scenario("filter is a comma seperated list of fully qualified names e.g. com.foo.Bar,com.foo.Baz") {
      given("a comma seperated list of classes")
      System.setProperty(PathHole.FILTER_PROPERTY_NAME, thisPackage + ".Foo1, " + thisPackage + ".Bar1")
      then("the ones that matches the filter should fail with ClassNotFoundException")
      intercept[ClassNotFoundException] {
        Class.forName(thisPackage + ".Foo1")
      }
      intercept[ClassNotFoundException] {
        Class.forName(thisPackage + ".Bar1")
      }
      expect(this.getClass.getClassLoader) {
        Class.forName(thisPackage + ".Baz1").getClassLoader
      }
    }

    scenario("classes unmatched by filter are available as normal") {
      given("an empty filter")
      assert(System.getProperty(PathHole.FILTER_PROPERTY_NAME) === null)
      then("some arbitrary class should be available without any problems")
      expect(this.getClass.getClassLoader) {
        Class.forName(thisPackage + ".Foo0").getClassLoader
      }
    }



    scenario("special wildcard character * may be used e.g. com.*Test it matches everything") {
      given("a filter that includes a regex")
      System.setProperty(PathHole.FILTER_PROPERTY_NAME, thisPackage + ".B*2")
      then("the ones that matches the filter should fail with ClassNotFoundException")
      expect(this.getClass.getClassLoader) {
        Class.forName(thisPackage + ".Foo2").getClassLoader
      }
      intercept[ClassNotFoundException] {
        Class.forName(thisPackage + ".Bar2")
      }
      intercept[ClassNotFoundException] {
        Class.forName(thisPackage + ".Baz2")
      }
    }

    scenario("invalid filter entries such as com-foo!bar should be ignored with log warn") {
      given("a filter that includes a invalid entries")
      System.setProperty(PathHole.FILTER_PROPERTY_NAME, "FRACKING TOASTERS,com.foo.bar")
      then("warn log should be added each time loadClass is called")

      val logger = Logger.getGlobal;

      val formatter = new SimpleFormatter();
      val out = new ByteArrayOutputStream();
      val handler = new StreamHandler(out, formatter);
      logger.addHandler(handler);

      try {
        Class.forName(thisPackage + ".Foo3")
        handler.flush();
        assert(out.toString().contains("FRACKING TOASTERS"))
        out.reset()
        Class.forName(thisPackage + ".Bar3")
        handler.flush();
        assert(out.toString().contains("FRACKING TOASTERS"))
      } finally {
        logger.removeHandler(handler);
      }
    }
  }

  before(System.clearProperty(PathHole.FILTER_PROPERTY_NAME))
  after(System.clearProperty(PathHole.FILTER_PROPERTY_NAME))
}

///////////////////////////////////////////////////
// DO NOT USE THE FOLLOWING CLASSES ANYWHERE !!! //
///////////////////////////////////////////////////

// the following classess will not be loaded as evident from JLS section 12.4
// since the agent is reading the filter property from System each time on loadClass
// we can modify the System filter property to test various options without having to having to include build tool
// i.e. have N maven surefire profiles for each specific -Dpath_hole.filter=.... property

class Foo0 {}

class Foo1 {}

class Bar1 {}

class Baz1 {}

class Foo2 {}

class Bar2 {}

class Baz2 {}

class Foo3 {}

class Bar3 {}