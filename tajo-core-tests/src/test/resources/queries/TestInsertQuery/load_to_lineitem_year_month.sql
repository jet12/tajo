INSERT INTO lineitem_year_month SELECT *, SUBSTR(l_shipdate, 1,4) as year, SUBSTR(l_shipdate, 6, 2) as month FROM default.lineitem;