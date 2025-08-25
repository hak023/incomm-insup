################################################################################
# Automatically-generated file. Do not edit!
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
CPP_SRCS += \
../src/inas_ext_gw_plugin.cpp \
../src/ingw.cpp \
../src/inmg.cpp \
../src/insup1.cpp \
../src/insup2.cpp \
../src/insup_protocol.cpp 

OBJS += \
./src/inas_ext_gw_plugin.o \
./src/ingw.o \
./src/inmg.o \
./src/insup1.o \
./src/insup2.o \
./src/insup_protocol.o 

CPP_DEPS += \
./src/inas_ext_gw_plugin.d \
./src/ingw.d \
./src/inmg.d \
./src/insup1.d \
./src/insup2.d \
./src/insup_protocol.d 


# Each subdirectory must supply rules for building sources it contributes
src/%.o: ../src/%.cpp
	@echo 'Building file: $<'
	@echo 'Invoking: GCC C++ Compiler'
	g++ -D_RELEASE -DLINUX64 -I../src -I../../dep_libs -I../../dep_libs/linux64 -I../../dep_libs/linux64/iniparser -I../../dep_libs/linux64/apr -O3 -g -Wall -c -fmessage-length=0 -fPIC -MMD -MP -MF"$(@:%.o=%.d)" -MT"$(@)" -o "$@" "$<"
	@echo 'Finished building: $<'
	@echo ' '


