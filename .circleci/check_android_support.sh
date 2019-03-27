#!/bin/bash

PASS=1
REPO_ROOT=$(git rev-parse --show-toplevel)
if [[ -z $REPO_ROOT ]]; then
    echo "Could not detect git repo root"
    exit 2
fi

repeatable=$(grep -r --include \*.java java.lang.annotation.Repeatable "$REPO_ROOT")
getannotationsbytype=$(grep -r --include \*.java AnnotatedElement.getAnnotationsByType "$REPO_ROOT")
stream=$(grep -r --include \*.java java.util.stream "$REPO_ROOT")
stream2=$(grep -r --include \*.java Arrays.stream "$REPO_ROOT")
functionalinterface=$(grep -r --include \*.java java.lang.FunctionalInterface "$REPO_ROOT")
reflectisdefault=$(grep -r --include \*.java java.lang.reflect.isDefault "$REPO_ROOT")
function=$(grep -r --include \*.java java.util.function "$REPO_ROOT")

if [[ ! -z $repeatable ]]; then
    echo -e "Error: Usage of java.lang.annotation.Repeatable found:\n$repeatable"
    PASS=0
fi
if [[ ! -z $getannotationsbytype ]]; then
    echo -e "Error: Usage of AnnotatedElement.getAnnotationsByType found:\n$getannotationsbytype"
    PASS=0
fi
if [[ ! -z $stream ]]; then
    echo -e "Error: Usage of java.util.stream found:\n$stream"
    PASS=0
fi
if [[ ! -z $stream2 ]]; then
    echo -e "Error: Usage of Arrays.stream found:\n$stream2"
    PASS=0
fi
if [[ ! -z $functionalinterface ]]; then
    echo -e "Error: Usage of java.lang.FunctionalInterface found:\n$functionalinterface"
    PASS=0
fi
if [[ ! -z $reflectisdefault ]]; then
    echo -e "Error: Usage of java.lang.reflect.isDefault found:\n$reflectisdefault"
    PASS=0
fi
if [[ ! -z $function ]]; then
    echo -e "Error: Usage of java.util.function found:\n$function"
    PASS=0
fi

if (( $PASS == 0 )); then
    echo ""
    echo "Unfortunately the APIs listed above are not supported by Android"
    echo "below API version 24, so they should not be used in SaltyRTC."
    exit 1
else
    echo "No forbidden APIs found"
    exit 0
fi
