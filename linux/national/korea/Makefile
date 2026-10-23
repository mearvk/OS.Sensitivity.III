CC ?= cc
CXX ?= c++
CFLAGS ?= -std=c11 -Wall -Wextra -Wpedantic -O2
CXXFLAGS ?= -std=c++17 -Wall -Wextra -Wpedantic -O2

C_TARGET := korea-eval
CPP_TARGET := korea-eval-cpp

.PHONY: all c cpp clean check

all: c cpp

c: $(C_TARGET)

cpp: $(CPP_TARGET)

$(C_TARGET): korea.c korea.h
	$(CC) $(CFLAGS) -o $@ korea.c

$(CPP_TARGET): korea.cpp korea.h
	$(CXX) $(CXXFLAGS) -o $@ korea.cpp

check: c
	./$(C_TARGET)

clean:
	rm -f $(C_TARGET) $(CPP_TARGET)
