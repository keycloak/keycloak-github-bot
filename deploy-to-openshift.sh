#! /bin/bash

# login to the OpenShift cluster before launching this script

# switch to the right project
oc project prod-keycloak-bot

mvn clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.native.container-build=true -Dnative

# add kubernetes.io/tls-acme: 'true' to the route to renew the SSL certificate automatically