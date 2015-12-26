#!/usr/bin/python

# This depends upon pyasn1, available from http://pyasn1.sourceforge.net/
from pyasn1.type import char
from pyasn1.type import univ
from pyasn1.codec.der import encoder as der

if __name__ == '__main__':
    topics = univ.Sequence()
    
    topics[0] = char.UTF8String("com.relayrides.pushy")
    topics[1] = univ.Sequence()
    topics[1][0] = char.UTF8String("app")
    topics[2] = char.UTF8String("com.relayrides.pushy.voip")
    topics[3] = univ.Sequence()
    topics[3][0] = char.UTF8String("voip")
    topics[4] = char.UTF8String("com.relayrides.pushy.complication")
    topics[5] = univ.Sequence()
    topics[5][0] = char.UTF8String("complication")
    
    print ''.join([byte.encode("hex") for byte in der.encode(topics)])