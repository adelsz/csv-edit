(ns csv-app.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [csv-app.core-test]
   [csv-app.common-test]))

(enable-console-print!)

(doo-tests 'csv-app.core-test
           'csv-app.common-test)
