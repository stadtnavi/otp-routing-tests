## OTP test routes

Runs a number of test routes against stadtnavi's staging instance.

To add a route, place a location in `locations.csv`. These locations are then
used to generate start/end location pairs for routing requests. In other words
if there are n locations then there will be (n^2)-n routes.

The output can be viewed at [Github Actions]().

